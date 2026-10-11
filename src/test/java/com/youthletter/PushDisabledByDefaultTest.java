package com.youthletter;

import com.youthletter.domain.notification.push.PushService;
import com.youthletter.domain.notification.push.PushType;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 설정하지 않으면 푸시는 꺼져 있고 외부로 아무것도 보내지 않는다. */
@SpringBootTest(properties = {"crawler.cron=-", "push.expo-url=http://127.0.0.1:9/never"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class PushDisabledByDefaultTest {
    @Autowired PushService push;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;

    @Test void nothingIsSentWhenDisabled() throws Exception {
        String k = UUID.randomUUID().toString().substring(0, 8);
        Long u = jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?,'t',?) RETURNING id", Long.class, k + "@test.invalid", "d" + k);
        jdbc.update("INSERT INTO device_tokens(user_id,device_id,token,platform) VALUES (?, 'p', ?, 'IOS')", u, "ExponentPushToken[" + k + "]");
        assertThat(push.isEnabled()).isFalse();
        assertThat(push.send(u, PushType.TEST, null, "t", "b", Map.of()).attempted()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_logs WHERE user_id=?", Integer.class, u)).isZero();
        mvc.perform(post("/api/me/push/test").header("Authorization", "Bearer " + tokens.createAccessToken(u, k + "@test.invalid", "USER")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(5006));
    }
}
