package com.medtrack.audit.controller;

import com.medtrack.audit.dto.AuditLogResponse;
import com.medtrack.audit.repository.AuditQueryRepository;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/audit-logs", "/api/v1/audit/logs"})
public class AuditController {
    private final AuditQueryRepository logs;

    public AuditController(AuditQueryRepository l) {
        this.logs = l;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('AUDITOR')")
    public Page<AuditLogResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return logs.findAll(PageRequest.of(page, Math.min(100, Math.max(1, size)), Sort.by(Sort.Direction.DESC, "createdAt")))
                   .map(AuditLogResponse::of);
    }
}