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

    public String triggerArgoForecast(String tenantId, String algorithm) {
        String algo = (algorithm != null && !algorithm.isBlank()) ? algorithm : "prophet";
        log.info("Triggering Argo forecast workflow for tenant {} using algorithm={}", tenantId, algo);
        try {
            String workflowName = argoWorkflowService.submitForecastWorkflow(tenantId, algo);
            log.info("Argo workflow '{}' submitted for tenant {} (algorithm={}) — it will write forecast data and refresh the MV on completion",
                    workflowName, tenantId, algo);
            return workflowName;
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

        // Remove only future forecast rows (preserve historical forecasts for months that now have actuals)
        factSalesDailyRepository.deleteFutureForecastsByTenant(tenantUuid);

        // Determine the latest actual month — forecast the next single month only
        LocalDate latestActual = actuals.stream()
                .map(FactSalesDaily::getTransactionDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
        LocalDate forecastFromMonth = latestActual.withDayOfMonth(1).plusMonths(1);

        int forecastMonths = 1;

        // Group by category + location
        Map<String, List<FactSalesDaily>> grouped = actuals.stream().collect(
                Collectors.groupingBy(f -> f.getCategoryId() + "_" + f.getLocationId()));

        List<FactSalesDaily> forecasts = new ArrayList<>();

        grouped.forEach((key, rows) -> {
            int categoryId = rows.get(0).getCategoryId();
            int locationId = rows.get(0).getLocationId();

            // Compute monthly totals from actuals for this category+location
            Map<LocalDate, BigDecimal> monthlyAmounts = rows.stream().collect(
                    Collectors.groupingBy(
                            f -> f.getTransactionDate().withDayOfMonth(1),
                            Collectors.reducing(BigDecimal.ZERO, FactSalesDaily::getAmount, BigDecimal::add)));
            Map<LocalDate, Integer> monthlyUnits = rows.stream().collect(
                    Collectors.groupingBy(
                            f -> f.getTransactionDate().withDayOfMonth(1),
                            Collectors.summingInt(FactSalesDaily::getUnitsSold)));

            // Average monthly revenue and units across all actual months
            int actualMonthCount = monthlyAmounts.size();
            BigDecimal avgMonthlyAmount = monthlyAmounts.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(actualMonthCount), 2, RoundingMode.HALF_UP);
            double avgMonthlyUnits = monthlyUnits.values().stream()
                    .mapToInt(Integer::intValue).average().orElse(0);

            // Insert one row per day in each forecast month so the MV aggregates correctly
            for (int m = 0; m < forecastMonths; m++) {
                LocalDate monthStart = forecastFromMonth.plusMonths(m);
                int daysInMonth = monthStart.lengthOfMonth();
                // Distribute monthly total evenly across days of the month
                BigDecimal dailyAmount = avgMonthlyAmount.divide(
                        BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
                int dailyUnits = (int) Math.round(avgMonthlyUnits / daysInMonth);

                for (int d = 0; d < daysInMonth; d++) {
                    forecasts.add(FactSalesDaily.builder()
                            .transactionDate(monthStart.plusDays(d))
                            .tenantId(tenantUuid)
                            .categoryId(categoryId)
                            .locationId(locationId)
                            .amount(dailyAmount)
                            .unitsSold(dailyUnits)
                            .isForecast(true)
                            .build());
                }
            }
        });

        factSalesDailyRepository.saveAll(forecasts);
        log.info("Local forecast complete: {} rows for next month ({}) for tenant {}",
                forecasts.size(), forecastFromMonth, tenantId);
        return "LOCAL_FORECAST_COMPLETE:1_MONTH";
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
