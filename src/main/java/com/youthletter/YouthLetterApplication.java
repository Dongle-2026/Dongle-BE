package com.youthletter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // @Scheduled 크롤링, 알림 배치 활성화
public class YouthLetterApplication {

    public static void main(String[] args) {
        SpringApplication.run(YouthLetterApplication.class, args);
    }
}
