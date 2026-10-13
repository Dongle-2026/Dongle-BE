package com.youthletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.domain.news.client.dto.AiQuizResponse;
import com.youthletter.domain.news.service.AiJobService;
import com.youthletter.domain.quiz.service.QuizWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** 전용 격리 DB에서 실제 JVM 종료 후 claim 복구·중복 방지를 검사한다. 모델 호출은 하지 않는다. */
@EnabledIfEnvironmentVariable(named="RUN_JOB_RESTART_TEST", matches="true")
class AiJobProcessRecoveryTest {
    static DriverManagerDataSource dataSource() {
        String host=System.getenv().getOrDefault("DB_HOST","localhost");
        String port=System.getenv().getOrDefault("DB_PORT","15434");
        String database=System.getenv().getOrDefault("DB_NAME","youthletter_test");
        if (!database.equals("youthletter_test") || !Set.of("localhost","127.0.0.1").contains(host))
            throw new IllegalStateException("Use the isolated local test database");
        return new DriverManagerDataSource("jdbc:postgresql://"+host+":"+port+"/"+database,
                System.getenv().getOrDefault("DB_USERNAME","test"),System.getenv().getOrDefault("DB_PASSWORD","test1234"));
    }
    static AiJobService service(JdbcTemplate jdbc) {
        // QUIZ 경로만 실행한다. ARTICLE/CARD 저장기는 사용하지 않는다.
        return new AiJobService(jdbc,null,null,new QuizWriter(jdbc,new ObjectMapper()));
    }
    static AiQuizResponse fixture() {
        return new AiQuizResponse("process-test-fixture","v1", java.util.stream.IntStream.rangeClosed(1,3)
                .mapToObj(i -> new AiQuizResponse.Item("시험 질문 "+i,List.of("가","나","다","라"),0,"시험 해설",List.of(1))).toList());
    }
    Process start(String mode, long job, Path signal, Path log) throws Exception {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",
                Objects.requireNonNull(System.getProperty("test.runtime.classpath")),Probe.class.getName(),mode,Long.toString(job),signal.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    @Test void killedJvmCanBeRecoveredAndOldLeaseCannotWrite() throws Exception {
        var ds=dataSource();var jdbc=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));var jobs=service(jdbc);
        // 공유 큐 작업을 소비하지 않도록, 전용 DB의 비어 있는 큐에서만 실행한다.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE status IN ('QUEUED','RUNNING')",Integer.class)).isZero();
        Long article=jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category) VALUES (?,'process-test','시험','시험 본문','금융') RETURNING id",
                Long.class,"https://example.invalid/process-"+UUID.randomUUID());
        Path dir=Files.createTempDirectory("ai-job-recovery-");
        Process first=null,second=null;
        try {
            var job=tx.execute(s -> jobs.enqueue(article,AiJobService.Kind.QUIZ));
            Path signal=dir.resolve("claimed");
            first=start("claim",job.id(),signal,dir.resolve("first.log"));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!Files.exists(signal) && first.isAlive() && System.nanoTime()<deadline) Thread.sleep(50);
            assertThat(Files.exists(signal)).as("first JVM committed RUNNING claim").isTrue();
            String oldToken=jdbc.queryForObject("SELECT lease_token FROM ai_jobs WHERE id=?",String.class,job.id());
            var oldClaim=new AiJobService.Claim(jobs.get(job.id()),oldToken);
            first.destroyForcibly();assertThat(first.waitFor(10,TimeUnit.SECONDS)).isTrue();
            assertThat(jobs.get(job.id()).status()).isEqualTo("RUNNING");
            // 10분을 기다리는 대신 이 시험 작업의 lease만 만료시킨다.
            jdbc.update("UPDATE ai_jobs SET lease_until=now()-interval '1 second' WHERE id=?",job.id());
            second=start("recover",job.id(),dir.resolve("recovered"),dir.resolve("second.log"));
            assertThat(second.waitFor(20,TimeUnit.SECONDS)).isTrue();assertThat(second.exitValue()).isZero();
            assertThat(jobs.get(job.id()).status()).isEqualTo("SUCCEEDED");
            assertThat(jobs.get(job.id()).attempts()).isEqualTo(2);
            Boolean staleAccepted=tx.execute(s -> jobs.completeQuiz(oldClaim,fixture()));
            assertThat(staleAccepted).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM quizzes WHERE article_id=?",Integer.class,article)).isEqualTo(3);
        } finally {
            if(first!=null && first.isAlive()) { first.destroyForcibly();first.waitFor(10,TimeUnit.SECONDS); }
            if(second!=null && second.isAlive()) { second.destroyForcibly();second.waitFor(10,TimeUnit.SECONDS); }
            jdbc.update("DELETE FROM ai_jobs WHERE article_id=?",article);
            jdbc.update("DELETE FROM news_articles WHERE id=?",article);
        }
    }
    public static class Probe {
        public static void main(String[] args) throws Exception {
            var ds=dataSource();var jdbc=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));var jobs=service(jdbc);
            var claim=tx.execute(s -> jobs.claim().orElseThrow());
            if(claim.job().id()!=Long.parseLong(args[1])) throw new IllegalStateException("Unexpected job");
            if(args[0].equals("claim")) {
                Files.writeString(Path.of(args[2]),"claimed");
                while(true) Thread.sleep(1000);
            }
            if(!Boolean.TRUE.equals(tx.execute(s -> jobs.completeQuiz(claim,fixture())))) throw new IllegalStateException("Completion rejected");
            if(Boolean.TRUE.equals(tx.execute(s -> jobs.completeQuiz(claim,fixture())))) throw new IllegalStateException("Duplicate completion accepted");
        }
    }
}
