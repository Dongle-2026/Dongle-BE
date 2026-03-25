package com.youthletter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class YouthLetterApplicationTests {

    @Test
    void contextLoads() {
        // Spring Context가 정상적으로 뜨는지 검증
        // DB 연결, Bean 등록, 설정 로딩 등 전반적인 초기화 확인
    }
}
