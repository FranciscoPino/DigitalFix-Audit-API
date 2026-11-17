package cl.duoc.digitalfix.audit.interfaces;

import cl.duoc.digitalfix.audit.domain.TimelineEvent;
import cl.duoc.digitalfix.audit.infrastructure.JdbcAuditRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("@securityProperties.disabled() or hasAnyAuthority('Admin', 'Auditor', 'ROLE_Admin', 'ROLE_Auditor')")
public class AuditController {
    private final JdbcAuditRepository repository;

    public AuditController(JdbcAuditRepository repository) { this.repository = repository; }

    @GetMapping("/{workOrderId}")
    public List<TimelineEvent> byWorkOrder(@PathVariable UUID workOrderId) {
        return repository.findByWorkOrderId(workOrderId);
    }

    @GetMapping
    public List<TimelineEvent> search(@RequestParam(required = false) String userId,
                                      @RequestParam(required = false) String eventType) {
        return repository.findAll(userId, eventType);
    }
}
