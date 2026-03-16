package com.qcom.salesanalyzer.orchestrator.activity;

import com.qcom.salesanalyzer.orchestrator.repository.FactSalesDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshInsightsActivityImpl implements RefreshInsightsActivity {

    private final FactSalesDailyRepository factSalesDailyRepository;

    @Override
    @Transactional
    public void refreshMaterializedView() {
        log.info("Refreshing materialized view mv_final_sales_insights...");
        factSalesDailyRepository.refreshMaterializedView();
        log.info("Materialized view refreshed successfully.");
    }
}
