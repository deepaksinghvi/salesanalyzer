package com.qcom.salesanalyzer.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/forecast")
public class ForecastProxyController {

    @Value("${app.forecaster.base-url}")
    private String forecasterBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/trigger")
    public ResponseEntity<Map> triggerForecast(@RequestBody Map<String, String> body) {
        log.info("Proxying forecast trigger for tenantId={}", body.get("tenantId"));
        return restTemplate.postForEntity(forecasterBaseUrl + "/api/forecast/trigger", body, Map.class);
    }

    @PostMapping("/run-local")
    public ResponseEntity<Map> runLocalForecast(@RequestBody Map<String, String> body) {
        log.info("Proxying local forecast for tenantId={}", body.get("tenantId"));
        return restTemplate.postForEntity(forecasterBaseUrl + "/api/forecast/run-local", body, Map.class);
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> clearForecast(@PathVariable String tenantId) {
        log.info("Proxying clear forecast for tenantId={}", tenantId);
        restTemplate.delete(forecasterBaseUrl + "/api/forecast/" + tenantId);
        return ResponseEntity.noContent().build();
    }
}
