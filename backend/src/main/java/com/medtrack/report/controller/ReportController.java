package com.medtrack.report.controller;

import com.medtrack.report.service.ReportService;
import com.medtrack.shared.exception.NotFoundException;
import com.medtrack.user.entity.User;
import com.medtrack.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reports;
    private final UserRepository users;

    public ReportController(ReportService r, UserRepository u) {
        this.reports = r;
        this.users = u;
    }

    @GetMapping(value = "/inventory", produces = "text/csv")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('AUDITOR') or hasRole('CENTRAL_WAREHOUSE_MANAGER') or hasRole('STORE_MANAGER')")
    public ResponseEntity<byte[]> inventoryCsv(
        @AuthenticationPrincipal String actorId,
        @RequestParam(required = false) UUID warehouseId
    ) {
        User actor = users.findById(UUID.fromString(actorId))
                .orElseThrow(() -> new NotFoundException("User"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventory.csv")
                .body(reports.inventoryCsv(actor, warehouseId));
    }

    @GetMapping(value = "/expiry", produces = "text/csv")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('AUDITOR') or hasRole('CENTRAL_WAREHOUSE_MANAGER') or hasRole('STORE_MANAGER')")
    public ResponseEntity<byte[]> expiryReportCsv(
        @AuthenticationPrincipal String actorId,
        @RequestParam(value = "days", required = false, defaultValue = "90") int days,
        @RequestParam(required = false) UUID warehouseId
    ) {
        User actor = users.findById(UUID.fromString(actorId))
                .orElseThrow(() -> new NotFoundException("User"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=expiry_report.csv")
                .body(reports.expiryReportCsv(actor, days, warehouseId));
    }

    @GetMapping("/expiry/data")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('AUDITOR') or hasRole('CENTRAL_WAREHOUSE_MANAGER') or hasRole('STORE_MANAGER')")
    public List<Map<String, Object>> expiryReportData(
        @AuthenticationPrincipal String actorId,
        @RequestParam(value = "days", required = false, defaultValue = "90") int days,
        @RequestParam(required = false) UUID warehouseId
    ) {
        User actor = users.findById(UUID.fromString(actorId))
                .orElseThrow(() -> new NotFoundException("User"));
        return reports.expiryReportData(actor, days, warehouseId);
    }
}