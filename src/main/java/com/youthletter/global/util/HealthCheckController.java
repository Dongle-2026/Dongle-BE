package com.youthletter.global.util;

import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Health", description = "서버 상태 확인")
@RestController
@RequiredArgsConstructor
public class HealthCheckController {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;

    @Operation(summary = "전체 헬스 체크", description = "서버, DB, Redis 연결 상태를 확인합니다")
    @GetMapping({"/actuator/health", "/api/health"})
    public GlobalResponse<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("server", "Spring Boot running on port 8080");
        health.put("database", checkDatabase());
        health.put("redis", checkRedis());
        return GlobalResponse.ok(health);
    }

    private String checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3) ? "connected" : "unreachable";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private String checkRedis() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            return pong != null ? "connected" : "unreachable";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}
