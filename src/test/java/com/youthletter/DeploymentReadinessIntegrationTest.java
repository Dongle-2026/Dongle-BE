package com.youthletter;

import com.youthletter.global.util.SchemaReadinessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"crawler.cron=-", "app.cors.allowed-origins=http://localhost:3001,https://app.example.invalid"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class DeploymentReadinessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SchemaReadinessService schema;

    @Test void migratedDatabaseIsReady() throws Exception {
        assertThat(schema.isReady()).isTrue();
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schema").value("ready"));
    }

    @Test void incompleteJdbcTableIsDetectedWithoutTouchingSharedTables() {
        // 접속별 임시 테이블로 이전 스키마를 재현하고 공유 테이블은 보존한다.
        jdbc.execute("CREATE TEMP TABLE quizzes (id bigint) ON COMMIT DROP");
        assertThat(schema.isReady()).isFalse();
    }

    @Test void configuredWebOriginsCanSendAuthorizationHeader() throws Exception {
        for (String origin : new String[]{"http://localhost:3001", "https://app.example.invalid"}) {
            mvc.perform(options("/api/me/stats").header("Origin", origin)
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }

    @Test void unknownWebOriginIsRejected() throws Exception {
        mvc.perform(options("/api/me/stats").header("Origin", "https://unknown.example.invalid")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
