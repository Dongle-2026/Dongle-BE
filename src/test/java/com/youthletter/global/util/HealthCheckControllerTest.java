package com.youthletter.global.util;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.*;
import javax.sql.DataSource;
import java.sql.Connection;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class HealthCheckControllerTest {
    @Test void healthyDependenciesReturn200AndCloseConnections() throws Exception {
        var schema = mock(SchemaReadinessService.class);
        when(schema.isReady()).thenReturn(true);
        var ds = mock(DataSource.class);
        var db = mock(Connection.class);
        when(ds.getConnection()).thenReturn(db);
        when(db.isValid(3)).thenReturn(true);
        RedisTemplate<String,Object> redis = mock(RedisTemplate.class);
        var factory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        var response = new HealthCheckController(ds, redis, schema).healthCheck();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData().get("status")).isEqualTo("UP");
        assertThat(response.getBody().getData().get("schema")).isEqualTo("ready");
        verify(db).close(); verify(connection).close();
    }
    @Test void dependencyErrorsReturn503WithoutInternalDetails() throws Exception {
        var schema = mock(SchemaReadinessService.class);
        when(schema.isReady()).thenReturn(true);
        var ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new java.sql.SQLException("secret database address"));
        RedisTemplate<String,Object> redis = mock(RedisTemplate.class);
        var response = new HealthCheckController(ds, redis, schema).healthCheck();
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getData().get("status")).isEqualTo("DOWN");
        assertThat(response.getBody().getData().toString()).doesNotContain("secret");
    }
    @Test void missingSchemaReturns503EvenWhenConnectionsAreHealthy() throws Exception {
        var ds = mock(DataSource.class);
        var db = mock(Connection.class);
        when(ds.getConnection()).thenReturn(db);
        when(db.isValid(3)).thenReturn(true);
        RedisTemplate<String,Object> redis = mock(RedisTemplate.class);
        var factory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        var schema = mock(SchemaReadinessService.class);
        var controller = new HealthCheckController(ds, redis, schema);
        var response = controller.healthCheck();
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getData()).containsEntry("database", "connected")
                .containsEntry("redis", "connected").containsEntry("schema", "unavailable");
        when(schema.isReady()).thenThrow(new IllegalStateException("secret SQL and password"));
        assertThat(controller.healthCheck().getBody().getData().toString()).doesNotContain("secret", "password");
    }
}
