package cl.duoc.digitalfix.audit.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record TimelineEvent(
        UUID id,
        UUID workOrderId,
        String type,
        String userId,
        String role,
        OffsetDateTime timestamp,
        String sourceIp,
        Map<String, Object> payload) {
}
