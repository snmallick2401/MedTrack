package com.medtrack.report.controller;

import com.medtrack.report.service.ReportService;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService r) {
        this.reports = r;
    }

    @GetMapping(value = "/inventory", produces = "text/csv")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('AUDITOR') or hasRole('CENTRAL_WAREHOUSE_MANAGER')")
    public ResponseEntity<byte[]> inventoryCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventory.csv")
                .body(reports.inventoryCsv());
    }

    @GetMapping(value = "/expiry", produces = "text/csv")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('AUDITOR') or hasRole('CENTRAL_WAREHOUSE_MANAGER') or hasRole('STORE_MANAGER')")
    public ResponseEntity<byte[]> expiryReportCsv(@RequestParam(value = "days", required = false, defaultValue = "90") int days) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=expiry_report.csv")
                .body(reports.expiryReportCsv(days));
    }

    @GetMapping("/expiry/data")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('AUDITOR') or hasRole('CENTRAL_WAREHOUSE_MANAGER') or hasRole('STORE_MANAGER')")
    public List<Map<String, Object>> expiryReportData(@RequestParam(value = "days", required = false, defaultValue = "90") int days) {
        return reports.expiryReportData(days);
    }
}