package com.qcom.salesanalyzer.forecaster.service;

import com.qcom.salesanalyzer.forecaster.entity.FactSalesDaily;
import com.qcom.salesanalyzer.forecaster.repository.FactSalesDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    private final FactSalesDailyRepository factSalesDailyRepository;
    private final ArgoWorkflowService argoWorkflowService;
    private final ApplicationContext applicationContext;

    private ForecastService self() {
        return applicationContext.getBean(ForecastService.class);
    }

    public String triggerArgoForecast(String tenantId) {
        log.info("Triggering Argo forecast workflow for tenant {}", tenantId);
        try {
            return argoWorkflowService.submitForecastWorkflow(tenantId);
        } catch (Exception e) {
            log.error("Argo workflow submission failed: {}", e.getMessage(), e);
            log.info("Falling back to local linear forecast for tenant {}", tenantId);
            return self().runLocalForecast(tenantId);
        }
    }

    @Transactional
    public String runLocalForecast(String tenantId) {
        UUID tenantUuid = UUID.fromString(tenantId);
        List<FactSalesDaily> actuals = factSalesDailyRepository.findActualsByTenant(tenantUuid);
        if (actuals.isEmpty()) {
            log.warn("No actuals found for tenant {}, skipping forecast", tenantId);
            return "NO_DATA";
        }

        // Remove old forecasts
        factSalesDailyRepository.deleteByTenantIdAndIsForecastTrue(tenantUuid);

        // Group by category + location, compute simple moving average for next 30 days
        Map<String, List<FactSalesDaily>> grouped = actuals.stream().collect(
                Collectors.groupingBy(f -> f.getCategoryId() + "_" + f.getLocationId()));

        List<FactSalesDaily> forecasts = new ArrayList<>();
        LocalDate forecastStart = LocalDate.now().plusDays(1);

        grouped.forEach((key, rows) -> {
            int categoryId = rows.get(0).getCategoryId();
            int locationId = rows.get(0).getLocationId();
            int windowSize = Math.min(30, rows.size());
            List<FactSalesDaily> window = rows.subList(rows.size() - windowSize, rows.size());

            BigDecimal avgAmount = window.stream()
                    .map(FactSalesDaily::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(windowSize), 2, RoundingMode.HALF_UP);

            double avgUnits = window.stream().mapToInt(FactSalesDaily::getUnitsSold).average().orElse(0);

            for (int i = 0; i < 30; i++) {
                forecasts.add(FactSalesDaily.builder()
                        .transactionDate(forecastStart.plusDays(i))
                        .tenantId(tenantUuid)
                        .categoryId(categoryId)
                        .locationId(locationId)
                        .amount(avgAmount)
                        .unitsSold((int) avgUnits)
                        .isForecast(true)
                        .build());
            }
        });

        factSalesDailyRepository.saveAll(forecasts);
        log.info("Local forecast complete: {} rows inserted for tenant {}", forecasts.size(), tenantId);
        return "LOCAL_FORECAST_COMPLETE";
    }

    @Transactional
    public void clearForecast(String tenantId) {
        UUID tenantUuid = UUID.fromString(tenantId);
        factSalesDailyRepository.deleteByTenantIdAndIsForecastTrue(tenantUuid);
        log.info("Cleared forecast data for tenant {}", tenantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshMv() {
        factSalesDailyRepository.refreshMaterializedView();
        log.info("Materialized view refreshed");
    }
}
