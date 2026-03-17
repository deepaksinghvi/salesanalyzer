package com.qcom.salesanalyzer.gateway.controller;

import com.qcom.salesanalyzer.gateway.entity.Tenant;
import com.qcom.salesanalyzer.gateway.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastProxyController {

    @Value("${app.orchestrator.base-url}")
    private String orchestratorBaseUrl;

    @Value("${app.forecaster.base-url}")
    private String forecasterBaseUrl;

    private final TenantRepository tenantRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('Admin', 'SuperAdmin')")
    public ResponseEntity<Map> triggerForecast(@RequestBody Map<String, String> body) {
        String tenantId = body.get("tenantId");
        String algorithm = body.getOrDefault("algorithm", "xgboost");

        String companyName = tenantRepository.findById(UUID.fromString(tenantId))
                .map(Tenant::getCompanyName)
                .orElse("unknown");

        log.info("Routing forecast through Temporal for tenant={} ({}), algorithm={}", companyName, tenantId, algorithm);
        return restTemplate.postForEntity(
                orchestratorBaseUrl + "/api/workflows/trigger-forecast",
                Map.of("tenantId", tenantId, "algorithm", algorithm, "tenantName", companyName),
                Map.class);
    }

    @PostMapping("/run-local")
    public ResponseEntity<Map> runLocalForecast(@RequestBody Map<String, String> body) {
        log.info("Proxying local forecast for tenantId={}", body.get("tenantId"));
        return restTemplate.postForEntity(forecasterBaseUrl + "/api/forecast/run-local", body, Map.class);
    }

    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasAnyRole('Admin', 'SuperAdmin')")
    public ResponseEntity<Void> clearForecast(@PathVariable String tenantId) {
        log.info("Proxying clear forecast for tenantId={}", tenantId);
        restTemplate.delete(forecasterBaseUrl + "/api/forecast/" + tenantId);
        return ResponseEntity.noContent().build();
    }
}
