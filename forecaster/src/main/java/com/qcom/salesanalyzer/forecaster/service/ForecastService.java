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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public String triggerArgoForecast(String tenantId, String algorithm, String horizon, String callbackUrl) {
        String algo = (algorithm != null && !algorithm.isBlank()) ? algorithm : "prophet";
        String h = (horizon != null && !horizon.isBlank()) ? horizon : "1m";
        int horizonDays = computeHorizonDays(tenantId, h);
        log.info("Triggering Argo forecast for tenant {} using algorithm={}, horizon={} ({}d)",
                tenantId, algo, h, horizonDays);
        try {
            String workflowName = argoWorkflowService.submitForecastWorkflow(tenantId, algo, horizonDays, h, callbackUrl);
            log.info("Argo workflow '{}' submitted for tenant {} (algorithm={}, horizon={}d)",
                    workflowName, tenantId, algo, horizonDays);
            return workflowName;
        } catch (Exception e) {
            log.error("Argo workflow submission failed: {}", e.getMessage(), e);
            log.info("Falling back to local linear forecast for tenant {}", tenantId);
            return self().runLocalForecast(tenantId, h);
        }
    }

    @Transactional
    public String runLocalForecast(String tenantId, String horizon) {
        UUID tenantUuid = UUID.fromString(tenantId);
        List<FactSalesDaily> actuals = factSalesDailyRepository.findActualsByTenant(tenantUuid);
        if (actuals.isEmpty()) {
            log.warn("No actuals found for tenant {}, skipping forecast", tenantId);
            return "NO_DATA";
        }

        boolean isWeekly = horizon != null && horizon.endsWith("w");

        LocalDate latestActual = actuals.stream()
                .map(FactSalesDaily::getTransactionDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        int horizonDays = computeHorizonDaysFromDate(latestActual, horizon);
        LocalDate forecastStart = latestActual.plusDays(1);
        LocalDate forecastEnd = forecastStart.plusDays(horizonDays - 1);

        if (isWeekly) {
            // Weekly: delete all existing forecasts (clean slate)
            factSalesDailyRepository.deleteByTenantIdAndIsForecastTrue(tenantUuid);
            log.info("Weekly forecast: cleared all existing forecasts for tenant {}", tenantId);
        } else {
            // Month+: delete forecasts only for the date range we're about to generate
            // This replaces overlapping data but preserves forecast months outside this range
            factSalesDailyRepository.deleteForecastsByTenantAndDateRange(
                    tenantUuid, forecastStart, forecastEnd);
            log.info("Month+ forecast: cleared forecasts from {} to {} for tenant {}",
                    forecastStart, forecastEnd, tenantId);
        }

        // Group by category + location
        Map<String, List<FactSalesDaily>> grouped = actuals.stream().collect(
                Collectors.groupingBy(f -> f.getCategoryId() + "_" + f.getLocationId()));

        // Compute average daily amount across all actual days (not hardcoded 30)
        long totalActualDays = actuals.stream()
                .map(FactSalesDaily::getTransactionDate)
                .distinct()
                .count();

        List<FactSalesDaily> forecasts = new ArrayList<>();

        grouped.forEach((key, rows) -> {
            int categoryId = rows.get(0).getCategoryId();
            int locationId = rows.get(0).getLocationId();

            BigDecimal totalAmount = rows.stream()
                    .map(FactSalesDaily::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int totalUnits = rows.stream()
                    .mapToInt(FactSalesDaily::getUnitsSold)
                    .sum();

            // Daily average based on actual number of days in the data
            BigDecimal dailyAmount = totalAmount.divide(
                    BigDecimal.valueOf(totalActualDays), 2, RoundingMode.HALF_UP);
            int dailyUnits = Math.max(1, (int) Math.round((double) totalUnits / totalActualDays));

            for (int d = 0; d < horizonDays; d++) {
                LocalDate date = forecastStart.plusDays(d);
                forecasts.add(FactSalesDaily.builder()
                        .transactionDate(date)
                        .tenantId(tenantUuid)
                        .categoryId(categoryId)
                        .locationId(locationId)
                        .amount(dailyAmount)
                        .unitsSold(dailyUnits)
                        .isForecast(true)
                        .build());
            }
        });

        factSalesDailyRepository.saveAll(forecasts);
        log.info("Local forecast: {} rows for {} days ({} to {}) for tenant {}",
                forecasts.size(), horizonDays, forecastStart, forecastEnd, tenantId);
        return "LOCAL_FORECAST_COMPLETE:" + horizon;
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

    /**
     * Compute horizon days from the latest actual date for this tenant.
     */
    private int computeHorizonDays(String tenantId, String horizon) {
        UUID tenantUuid = UUID.fromString(tenantId);
        List<FactSalesDaily> actuals = factSalesDailyRepository.findActualsByTenant(tenantUuid);
        LocalDate latestActual = actuals.stream()
                .map(FactSalesDaily::getTransactionDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
        return computeHorizonDaysFromDate(latestActual, horizon);
    }

    static int computeHorizonDaysFromDate(LocalDate latestActual, String horizon) {
        if (horizon == null || horizon.isBlank()) horizon = "1m";
        LocalDate start = latestActual.plusDays(1);

        return switch (horizon) {
            case "1w" -> 7;
            case "2w" -> 14;
            case "3w" -> 21;
            case "2m" -> {
                int days = 0;
                for (int i = 0; i < 2; i++) {
                    YearMonth ym = YearMonth.from(start.plusMonths(i));
                    days += ym.lengthOfMonth();
                }
                yield days;
            }
            case "1q" -> {
                int days = 0;
                for (int i = 0; i < 3; i++) {
                    YearMonth ym = YearMonth.from(start.plusMonths(i));
                    days += ym.lengthOfMonth();
                }
                yield days;
            }
            case "1y" -> {
                int days = 0;
                for (int i = 0; i < 12; i++) {
                    YearMonth ym = YearMonth.from(start.plusMonths(i));
                    days += ym.lengthOfMonth();
                }
                yield days;
            }
            default -> { // "1m"
                YearMonth ym = YearMonth.from(start);
                yield ym.lengthOfMonth();
            }
        };
    }
}
