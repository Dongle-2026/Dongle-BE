package com.youthletter.global.util;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** JPA validate가 검사하지 않는 JDBC 학습 테이블도 배포 준비 상태에 포함한다. */
@Service
@RequiredArgsConstructor
public class SchemaReadinessService {
    private static final String AUDIT_SQL = loadSql();
    private final JdbcTemplate jdbc;

    public boolean isReady() {
        return jdbc.query(AUDIT_SQL, (rs, row) -> rs.getString("table_name")).isEmpty();
    }

    private static String loadSql() {
        try {
            return new ClassPathResource("db/schema-readiness.sql").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
