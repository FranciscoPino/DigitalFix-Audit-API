package cl.duoc.digitalfix.audit.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfEnvironmentVariable(named = "DIGITALFIX_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class PostgresAuditIntegrationTest {
    @Test
    void storesEachEventOnceAndReturnsItsPayload() throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("DIGITALFIX_TEST_DB_URL"), "postgres", "test")) {
            connection.setAutoCommit(false);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.execute("CREATE SCHEMA audit");
            jdbc.execute("""
                    CREATE TABLE audit.timeline_events (event_id uuid PRIMARY KEY, event_type varchar(100) NOT NULL,
                    aggregate_type varchar(80) NOT NULL, aggregate_id uuid NOT NULL, actor_external_user_id varchar(200),
                    actor_role varchar(60), event_timestamp timestamptz NOT NULL, source_ip inet,
                    trace_id uuid, correlation_id uuid, payload jsonb NOT NULL)
                    """);
            JdbcAuditRepository repository = new JdbcAuditRepository(jdbc, new ObjectMapper());
            UUID eventId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            OffsetDateTime timestamp = OffsetDateTime.parse("2026-01-01T10:00:00Z");
            repository.insertEvent(eventId, "WORK_ORDER_CLOSED", orderId, "user-1", timestamp, "{\"status\":\"CLOSED\"}");
            repository.insertEvent(eventId, "WORK_ORDER_CLOSED", orderId, "user-1", timestamp, "{\"status\":\"CLOSED\"}");
            var events = repository.findByWorkOrderId(orderId);
            assertEquals(1, events.size());
            assertEquals("user-1", events.get(0).userId());
            assertEquals("CLOSED", events.get(0).payload().get("status"));
            connection.rollback();
        }
    }
}
