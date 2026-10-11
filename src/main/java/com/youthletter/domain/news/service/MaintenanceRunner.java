package com.youthletter.domain.news.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * DB 정비 작업을 서버 시작 시 1회 실행하고 종료한다(관리자 API 대신 운영자가 직접 실행).
 * 예: java -jar app.jar --ops.run=reclassify,thumbnails --spring.main.web-application-type=none
 * - reclassify: 기존 기사 카테고리 재분류
 * - thumbnails: 썸네일 없는 기사의 og:image를 100건씩 끝까지 채움
 * ops.run이 비어 있으면(기본) 아무것도 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceRunner implements ApplicationRunner {
    private final NewsCategoryService categoryService;
    private final ThumbnailBackfillService thumbnailService;
    private final ConfigurableApplicationContext context;

    @Value("${ops.run:}")
    private String tasks;

    @Value("${ops.exit-after-run:true}")
    private boolean exitAfterRun;

    @Override
    public void run(ApplicationArguments args) {
        List<String> todo = Arrays.stream(tasks.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList();
        if (todo.isEmpty()) return;
        for (String task : todo) {
            switch (task) {
                case "reclassify" -> log.info("[Ops] 카테고리 재분류: {}건 변경", categoryService.reclassifyAll());
                case "thumbnails" -> {
                    int total = 0;
                    ThumbnailBackfillService.Result r;
                    do {
                        r = thumbnailService.backfill(ThumbnailBackfillService.MAX_BATCH);
                        total += r.updated();
                        log.info("[Ops] 썸네일 배치: 확인 {}, 채움 {}, 이미지 없음 {}, 실패 {}", r.checked(), r.updated(), r.notFound(), r.failed());
                    } while (r.updated() > 0 && r.checked() == ThumbnailBackfillService.MAX_BATCH);
                    log.info("[Ops] 썸네일 채움 합계 {}건", total);
                }
                default -> log.warn("[Ops] 알 수 없는 작업: {}", task);
            }
        }
        if (exitAfterRun) System.exit(SpringApplication.exit(context, () -> 0));
    }
}
