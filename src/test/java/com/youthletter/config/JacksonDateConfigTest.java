package com.youthletter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonDateConfigTest {
    private static String write(LocalDateTime value, ZoneId serverZone) throws Exception {
        var mapper = new ObjectMapper().registerModule(new SimpleModule()
                .addSerializer(LocalDateTime.class, new JacksonDateConfig.KstOffsetSerializer(serverZone)));
        return mapper.writeValueAsString(value);
    }

    @Test void sameInstantPrintsTheSameKoreanTimeOnUtcAndSeoulServers() throws Exception {
        // 2026-10-18 09:00:03 KST = 00:00:03 UTC
        assertThat(write(LocalDateTime.of(2026, 10, 18, 0, 0, 3), ZoneOffset.UTC))
                .isEqualTo("\"2026-10-18T09:00:03+09:00\"");
        assertThat(write(LocalDateTime.of(2026, 10, 18, 9, 0, 3), ZoneId.of("Asia/Seoul")))
                .isEqualTo("\"2026-10-18T09:00:03+09:00\"");
    }
}
