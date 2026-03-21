package com.chainpay.core.audit.service;

import com.chainpay.core.audit.domain.AuditLogEntry;
import com.chainpay.core.audit.repository.AuditLogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditExporterService {

    private final AuditLogEntryRepository auditLogRepository;

    @Transactional(readOnly = true)
    public String exportAuditLogs(String format) {
        List<AuditLogEntry> entries = auditLogRepository.findAll();

        if ("CSV".equalsIgnoreCase(format)) {
            StringBuilder csv = new StringBuilder();
            csv.append("ID,Timestamp,Actor,Action,Resource,Details\n");
            for (AuditLogEntry e : entries) {
                csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        e.getId(), e.getCreatedAt(), e.getActor(), e.getAction(), e.getResource(),
                        e.getDetails() != null ? e.getDetails().replace("\"", "'") : ""));
            }
            return csv.toString();
        }

        // JSON Fallback
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < entries.size(); i++) {
            AuditLogEntry e = entries.get(i);
            json.append(String.format("  {\"id\":\"%s\",\"actor\":\"%s\",\"action\":\"%s\",\"resource\":\"%s\",\"timestamp\":\"%s\"}%s\n",
                    e.getId(), e.getActor(), e.getAction(), e.getResource(), e.getCreatedAt(),
                    (i < entries.size() - 1) ? "," : ""));
        }
        json.append("]");
        return json.toString();
    }
}
