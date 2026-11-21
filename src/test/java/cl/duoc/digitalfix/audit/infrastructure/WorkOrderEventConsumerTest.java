package cl.duoc.digitalfix.audit.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkOrderEventConsumerTest {
    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID EVENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String EVENT = """
            {"eventId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","eventType":"WORK_ORDER_CLOSED",
             "aggregateType":"WORK_ORDER","aggregateId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
             "createdAt":"2026-01-01T10:00:00Z","payload":{"status":"CLOSED","changedBy":"user-1"}}
            """;

    @Test
    void recordsValidEventAndPropagatesDatabaseFailures() throws Exception {
        JdbcAuditRepository repository = mock(JdbcAuditRepository.class);
        WorkOrderEventConsumer consumer = new WorkOrderEventConsumer(new ObjectMapper(), repository);
        consumer.consume(EVENT);
        verify(repository).insertEvent(EVENT_ID, "WORK_ORDER_CLOSED", ORDER_ID, "user-1",
                OffsetDateTime.parse("2026-01-01T10:00:00Z"), "{\"status\":\"CLOSED\",\"changedBy\":\"user-1\"}");
        doThrow(new IllegalStateException("database unavailable")).when(repository)
                .insertEvent(eq(EVENT_ID), anyString(), eq(ORDER_ID), anyString(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> consumer.consume(EVENT));
    }

    @Test
    void rejectsMalformedEventsWithoutWriting() {
        JdbcAuditRepository repository = mock(JdbcAuditRepository.class);
        WorkOrderEventConsumer consumer = new WorkOrderEventConsumer(new ObjectMapper(), repository);
        assertThrows(Exception.class, () -> consumer.consume(EVENT.replace("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "bad")));
        assertThrows(Exception.class, () -> consumer.consume(EVENT.replace("\"user-1\"", "123")));
        assertThrows(Exception.class, () -> consumer.consume(EVENT.replace("2026-01-01T10:00:00Z", "bad")));
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(EVENT.replace("\"status\":\"CLOSED\"", "\"status\":\"CREATED\"")));
        assertThrows(IllegalArgumentException.class, () -> consumer.consume(EVENT.replace("\"eventId\"", "\"schemaVersion\":2,\"eventId\"")));
        verifyNoInteractions(repository);
    }
}
