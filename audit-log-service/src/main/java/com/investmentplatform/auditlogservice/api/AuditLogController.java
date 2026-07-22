package com.investmentplatform.auditlogservice.api;

import com.investmentplatform.auditlogservice.api.dto.AuditLogEntryResponse;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-log")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Returns all audit log entries for the given aggregate ID,
     * sorted by received time descending (most recent first).
     *
     * Example: GET /api/audit-log?aggregateId=550e8400-e29b-41d4-a716-446655440000
     */
    @GetMapping
    public ResponseEntity<List<AuditLogEntryResponse>> getByAggregateId(
            @RequestParam(required = false) String aggregateId) {

        if (aggregateId == null || aggregateId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<AuditLogEntryResponse> entries = auditLogRepository
                .findByAggregateIdOrderByReceivedAtDesc(aggregateId)
                .stream()
                .map(AuditLogEntryResponse::from)
                .toList();

        return ResponseEntity.ok(entries);
    }
}
