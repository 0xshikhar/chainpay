package com.chainpay.core.audit.api;

import com.chainpay.core.audit.domain.AuditLogEntry;
import com.chainpay.core.audit.repository.AuditLogEntryRepository;
import com.chainpay.core.audit.service.AuditExporterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogEntryRepository auditLogRepository;
    private final AuditExporterService auditExporterService;

    @GetMapping("/entries")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<List<AuditLogEntry>> getAuditEntries() {
        return ResponseEntity.ok(auditLogRepository.findAll());
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> exportAuditLogs(@RequestParam(value = "format", defaultValue = "CSV") String format) {
        String exportedData = auditExporterService.exportAuditLogs(format);
        MediaType mediaType = "CSV".equalsIgnoreCase(format) ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chainpay-audit-export." + format.toLowerCase())
                .contentType(mediaType)
                .body(exportedData);
    }
}
