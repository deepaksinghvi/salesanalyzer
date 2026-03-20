package com.qcom.salesanalyzer.forecaster.controller;

import com.qcom.salesanalyzer.forecaster.dto.ForecastRequest;
import com.qcom.salesanalyzer.forecaster.service.ForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> triggerForecast(@RequestBody ForecastRequest request) {
        String algo = request.getAlgorithm() != null ? request.getAlgorithm() : "prophet";
        String horizon = request.getHorizon() != null ? request.getHorizon() : "1m";
        log.info("Forecast trigger received for tenantId={}, algorithm={}, horizon={}",
                request.getTenantId(), algo, horizon);
        String result = forecastService.triggerArgoForecast(
                request.getTenantId(), algo, horizon, request.getCallbackUrl());
        if (result.startsWith("LOCAL_FORECAST_COMPLETE")) {
            forecastService.refreshSummaryForTenant(request.getTenantId());
        }
        return ResponseEntity.ok(Map.of("workflowName", result, "status", "SUBMITTED"));
    }

    @PostMapping("/run-local")
    public ResponseEntity<Map<String, String>> runLocalForecast(@RequestBody ForecastRequest request) {
        String horizon = request.getHorizon() != null ? request.getHorizon() : "1m";
        log.info("Local forecast triggered for tenantId={}, horizon={}", request.getTenantId(), horizon);
        String result = forecastService.runLocalForecast(request.getTenantId(), horizon);
        forecastService.refreshSummaryForTenant(request.getTenantId());
        return ResponseEntity.ok(Map.of("result", result));
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> clearForecast(@PathVariable String tenantId) {
        log.info("Clearing forecast data for tenantId={}", tenantId);
        forecastService.clearForecast(tenantId);
        forecastService.refreshSummaryForTenant(tenantId);
        return ResponseEntity.noContent().build();
    }
}
