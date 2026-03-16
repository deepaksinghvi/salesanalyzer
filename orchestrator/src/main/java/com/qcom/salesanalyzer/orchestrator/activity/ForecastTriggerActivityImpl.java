package com.qcom.salesanalyzer.orchestrator.activity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastTriggerActivityImpl implements ForecastTriggerActivity {

    private final RestTemplate restTemplate;

    @Value("${app.forecaster.base-url}")
    private String forecasterBaseUrl;

    @Override
    public void submitForecastRequest(String tenantId) {
        log.info("Submitting forecast request for tenant {}", tenantId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> payload = new HashMap<>();
            payload.put("tenantId", tenantId);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(forecasterBaseUrl + "/api/forecast/trigger", request, String.class);
            log.info("Forecast request submitted for tenant {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to submit forecast for tenant {}: {}", tenantId, e.getMessage(), e);
            throw new RuntimeException("Forecast submission failed: " + e.getMessage(), e);
        }
    }
}
