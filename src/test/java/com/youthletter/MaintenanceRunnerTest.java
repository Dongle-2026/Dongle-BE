package com.youthletter;

import com.youthletter.domain.news.service.MaintenanceRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** --ops.run=reclassify 로 기존 기사 카테고리를 재분류한다(테스트에서는 종료하지 않음). */
@SpringBootTest(properties = {"crawler.cron=-", "ops.run=reclassify", "ops.exit-after-run=false"})
@ActiveProfiles("test") @Transactional
class MaintenanceRunnerTest {
    @Autowired MaintenanceRunner runner;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    @Test void reclassifiesExistingArticles() throws Exception {
        Long id = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?,'t','아파트 전세가 급등','주택 청약','금융',now()) RETURNING id",
                Long.class, "ops-" + UUID.randomUUID());
        runner.run(new DefaultApplicationArguments());
        em.flush(); // 테스트 트랜잭션은 커밋되지 않으므로 SQL 확인 전 반영
        assertThat(jdbc.queryForObject("SELECT category FROM news_articles WHERE id=?", String.class, id)).isEqualTo("부동산");
    }

    @Test void keepsCuratedSampleCategories() throws Exception {
        Long sample = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?,'샘플 데이터','[샘플] 첫 월급 돈 관리','적금 저축 적금','취업',now()) RETURNING id",
                Long.class, "ops-sample-" + UUID.randomUUID());
        runner.run(new DefaultApplicationArguments());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT category FROM news_articles WHERE id=?", String.class, sample)).isEqualTo("취업");
    }
}
