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
        log.info("Forecast trigger received for tenantId={}, algorithm={}", request.getTenantId(), algo);
        String result = forecastService.triggerArgoForecast(request.getTenantId(), algo);
        // Only refresh MV immediately when local fallback was used;
        // Argo workflows refresh the MV themselves on completion.
        if (result.startsWith("LOCAL_FORECAST_COMPLETE")) {
            forecastService.refreshMv();
        }
        return ResponseEntity.ok(Map.of("workflowName", result, "status", "SUBMITTED"));
    }

    @PostMapping("/run-local")
    public ResponseEntity<Map<String, String>> runLocalForecast(@RequestBody ForecastRequest request) {
        log.info("Local forecast triggered for tenantId={}", request.getTenantId());
        String result = forecastService.runLocalForecast(request.getTenantId());
        forecastService.refreshMv();
        return ResponseEntity.ok(Map.of("result", result));
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> clearForecast(@PathVariable String tenantId) {
        log.info("Clearing forecast data for tenantId={}", tenantId);
        forecastService.clearForecast(tenantId);
        forecastService.refreshMv();
        return ResponseEntity.noContent().build();
    }
}
