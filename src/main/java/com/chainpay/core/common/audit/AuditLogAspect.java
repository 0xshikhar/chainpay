package com.chainpay.core.common.audit;

import com.chainpay.core.audit.domain.AuditLogEntry;
import com.chainpay.core.audit.repository.AuditLogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogEntryRepository auditLogRepository;

    @Around("@annotation(auditLog)")
    public Object logAudit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = (authentication != null && authentication.isAuthenticated()) ? authentication.getName() : "SYSTEM";

        Object result = joinPoint.proceed();

        try {
            AuditLogEntry entry = AuditLogEntry.builder()
                    .actor(actor)
                    .action(auditLog.action())
                    .resource(auditLog.resource().isEmpty() ? joinPoint.getSignature().toShortString() : auditLog.resource())
                    .details("Executed method: " + joinPoint.getSignature().getName() + " with args: " + Arrays.toString(joinPoint.getArgs()))
                    .build();

            auditLogRepository.save(entry);
            log.info("AOP Audit: Recorded action '{}' by actor '{}'", auditLog.action(), actor);
        } catch (Exception ex) {
            log.error("Failed to record AOP audit log: {}", ex.getMessage());
        }

        return result;
    }
}
