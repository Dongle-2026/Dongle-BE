package com.youthletter;

import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 노션 A26·A27 알림 설정: 기본값, 부분 변경, 소유권, 인증. */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class NotificationSettingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired jakarta.persistence.EntityManager em;
    Long userId, otherId;
    String bearer, otherBearer;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        String key = "noti-" + UUID.randomUUID();
        userId = user(key); otherId = user(key + "o");
        bearer = "Bearer " + tokens.createAccessToken(userId, key + "@test.invalid", "USER");
        otherBearer = "Bearer " + tokens.createAccessToken(otherId, key + "o@test.invalid", "USER");
    }
    Long user(String key) {
        return jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?,'t',?) RETURNING id",
                Long.class, key + "@test.invalid", key.substring(key.length() - 20));
    }
    int rows(Long id) { return jdbc.queryForObject("SELECT count(*) FROM notification_settings WHERE user_id=?", Integer.class, id); }

    @Test void defaultsAreAllOnWithoutCreatingRow() throws Exception {
        mvc.perform(get("/api/me/notification-settings").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyDigest").value(true))
                .andExpect(jsonPath("$.data.quizReminder").value(true))
                .andExpect(jsonPath("$.data.streakAlert").value(true));
        assertThat(rows(userId)).isZero();
    }

    @Test void patchChangesOnlySentFieldsAndIsRepeatable() throws Exception {
        for (int i = 0; i < 2; i++)
            mvc.perform(patch("/api/me/notification-settings").header("Authorization", bearer)
                            .contentType("application/json").content("{\"quizReminder\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dailyDigest").value(true))
                    .andExpect(jsonPath("$.data.quizReminder").value(false))
                    .andExpect(jsonPath("$.data.streakAlert").value(true));
        mvc.perform(patch("/api/me/notification-settings").header("Authorization", bearer)
                        .contentType("application/json").content("{\"streakAlert\":false,\"dailyDigest\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyDigest").value(true))
                .andExpect(jsonPath("$.data.quizReminder").value(false))
                .andExpect(jsonPath("$.data.streakAlert").value(false));
        em.flush();
        assertThat(rows(userId)).isEqualTo(1);
        mvc.perform(get("/api/me/notification-settings").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.quizReminder").value(false)).andExpect(jsonPath("$.data.streakAlert").value(false));
        // 다른 사용자 설정에는 영향 없음
        mvc.perform(get("/api/me/notification-settings").header("Authorization", otherBearer))
                .andExpect(jsonPath("$.data.quizReminder").value(true)).andExpect(jsonPath("$.data.streakAlert").value(true));
    }

    @Test void rejectsInvalidAndAnonymous() throws Exception {
        mvc.perform(patch("/api/me/notification-settings").header("Authorization", bearer)
                .contentType("application/json").content("{\"quizReminder\":\"maybe\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/notification-settings")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/me/notification-settings").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(rows(userId)).isZero();
    }
}
