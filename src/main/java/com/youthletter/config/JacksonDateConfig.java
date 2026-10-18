package com.youthletter.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * API 응답의 LocalDateTime을 한국 시간 + 오프셋(예: 2026-10-18T09:00:03+09:00)으로 내보낸다.
 * 서버의 LocalDateTime은 JVM 기본 시간대 기준(DB TIMESTAMPTZ를 Hibernate가 변환)이라,
 * 오프셋 없이 내보내면 배포 환경(TZ=UTC / Asia/Seoul)에 따라 9시간 차이가 났다.
 */
@Configuration
public class JacksonDateConfig {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer offsetDateTimeOutput() {
        return builder -> builder.serializerByType(LocalDateTime.class, new KstOffsetSerializer(ZoneId.systemDefault()));
    }

    static final class KstOffsetSerializer extends JsonSerializer<LocalDateTime> {
        private final ZoneId serverZone;

        KstOffsetSerializer(ZoneId serverZone) {
            this.serverZone = serverZone;
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.atZone(serverZone).withZoneSameInstant(KST).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }
}
