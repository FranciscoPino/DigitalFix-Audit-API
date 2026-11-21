package cl.duoc.digitalfix.audit.infrastructure;

import cl.duoc.digitalfix.audit.domain.TimelineEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAuditRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void insertEvent(UUID eventId, String eventType, UUID aggregateId, String actor,
                            OffsetDateTime timestamp, String payload) {
        jdbc.update("""
                INSERT INTO audit.timeline_events
                    (event_id, event_type, aggregate_type, aggregate_id, actor_external_user_id, event_timestamp, payload)
                VALUES (?, ?, 'WORK_ORDER', ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventType, aggregateId, actor, timestamp, payload);
    }

    public List<TimelineEvent> findByWorkOrderId(UUID workOrderId) {
        return jdbc.query("SELECT * FROM audit.timeline_events WHERE aggregate_id = ? ORDER BY event_timestamp", this::map, workOrderId);
    }

    public List<TimelineEvent> findAll(String userId, String eventType) {
        return jdbc.query("""
                SELECT * FROM audit.timeline_events
                WHERE (CAST(? AS text) IS NULL OR actor_external_user_id = ?)
                  AND (CAST(? AS text) IS NULL OR event_type = ?)
                ORDER BY event_timestamp DESC
                """, this::map, userId, userId, eventType, eventType);
    }

    private TimelineEvent map(ResultSet rs, int row) throws SQLException {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rs.getString("payload"), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new SQLException("Invalid audit event payload", e);
        }
        return new TimelineEvent(rs.getObject("event_id", UUID.class), rs.getObject("aggregate_id", UUID.class),
                rs.getString("event_type"), rs.getString("actor_external_user_id"), rs.getString("actor_role"),
                rs.getObject("event_timestamp", OffsetDateTime.class),
                rs.getString("source_ip"), payload);
    }
}
