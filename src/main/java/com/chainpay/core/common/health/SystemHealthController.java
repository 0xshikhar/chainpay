package com.chainpay.core.common.health;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class SystemHealthController {

    private final SystemHealthService healthService;

    @GetMapping
    public ResponseEntity<SystemHealthService.SystemHealthStatus> getHealth() {
        return ResponseEntity.ok(healthService.checkSystemHealth());
    }
}
