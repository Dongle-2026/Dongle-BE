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
    public org.springframework.http.ResponseEntity<GlobalResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        String database = checkDatabase();
        String redis = checkRedis();
        boolean ready = "connected".equals(database) && "connected".equals(redis);
        health.put("status", ready ? "UP" : "DOWN");
        health.put("database", database);
        health.put("redis", redis);
        return ready ? org.springframework.http.ResponseEntity.ok(GlobalResponse.ok(health))
                : org.springframework.http.ResponseEntity.status(503).body(GlobalResponse.error(503, "dependencies unavailable", health));
    }

    private String checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3) ? "connected" : "unreachable";
        } catch (Exception e) {
            return "unreachable";
        }
    }

    private String checkRedis() {
        try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
            return "PONG".equals(connection.ping()) ? "connected" : "unreachable";
        } catch (Exception e) {
            return "unreachable";
        }
    }
}
