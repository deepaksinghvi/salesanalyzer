package com.qcom.salesanalyzer.orchestrator.activity;

import com.qcom.salesanalyzer.orchestrator.repository.FactSalesDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshInsightsActivityImpl implements RefreshInsightsActivity {

    private final FactSalesDailyRepository factSalesDailyRepository;
    private final RestTemplate restTemplate;

    @Value("${gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    @Override
    @Transactional
    public void refreshMaterializedView() {
        log.info("Refreshing materialized view mv_final_sales_insights (legacy)...");
        factSalesDailyRepository.refreshMaterializedView();
        log.info("Materialized view refreshed successfully.");
    }

    @Override
    public void refreshSummaryForTenant(String tenantId) {
        log.info("Refreshing summary table for tenant {}...", tenantId);
        String url = gatewayUrl + "/api/insights/" + tenantId + "/refresh";
        restTemplate.postForObject(url, null, String.class);
        log.info("Summary table refreshed for tenant {}", tenantId);
    }
}
