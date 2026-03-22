package com.chainpay.core.audit.service;

import com.chainpay.core.audit.domain.AuditLogEntry;
import com.chainpay.core.audit.repository.AuditLogEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditExporterServiceTest {

    @Mock
    private AuditLogEntryRepository auditLogRepository;

    @InjectMocks
    private AuditExporterService auditExporterService;

    @Test
    @DisplayName("Should export audit logs as CSV header and formatted rows")
    void testExportAuditLogs_CSVFormat() {
        AuditLogEntry entry = AuditLogEntry.builder()
                .id(UUID.randomUUID())
                .actor("admin")
                .action("POST_TRANSACTION")
                .resource("LedgerService")
                .details("Posted balanced transaction")
                .build();

        when(auditLogRepository.findAll()).thenReturn(List.of(entry));

        String csvOutput = auditExporterService.exportAuditLogs("CSV");

        assertNotNull(csvOutput);
        assertTrue(csvOutput.contains("ID,Timestamp,Actor,Action,Resource,Details"));
        assertTrue(csvOutput.contains("admin"));
        assertTrue(csvOutput.contains("POST_TRANSACTION"));
    }
}
