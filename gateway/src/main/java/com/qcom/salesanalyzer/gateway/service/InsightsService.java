package com.qcom.salesanalyzer.gateway.service;

import com.qcom.salesanalyzer.gateway.dto.DataRangeDto;
import com.qcom.salesanalyzer.gateway.repository.SalesInsightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsService {

    private final EntityManager entityManager;
    private final SalesInsightRepository salesInsightRepository;

    private static final Set<String> VALID_GRANULARITIES = Set.of("week", "month", "quarter", "year");

    public List<Map<String, Object>> getInsightsWithGranularity(UUID tenantId, String granularity, String period) {
        String trunc = VALID_GRANULARITIES.contains(granularity) ? granularity : "month";

        String sql = """
            SELECT
                fsd.tenant_id,
                DATE_TRUNC(:trunc, fsd.transaction_date)::date AS period_month,
                fsd.category_id,
                dc.name AS category_name,
                SUM(CASE WHEN fsd.is_forecast = FALSE THEN fsd.amount ELSE 0 END) AS actual_revenue,
                SUM(CASE WHEN fsd.is_forecast = TRUE  THEN fsd.amount ELSE 0 END) AS predicted_revenue,
                SUM(fsd.units_sold) AS total_units,
                RANK() OVER (
                    PARTITION BY fsd.tenant_id, DATE_TRUNC(:trunc, fsd.transaction_date)::date
                    ORDER BY SUM(fsd.amount) DESC
                ) AS category_rank
            FROM fact_sales_daily fsd
            JOIN dim_categories dc ON fsd.category_id = dc.category_id
            WHERE fsd.tenant_id = :tenantId
            """;

        if (!"all".equalsIgnoreCase(period)) {
            sql += " AND fsd.transaction_date >= :fromDate";
        }

        sql += " GROUP BY 1, 2, 3, 4 ORDER BY period_month DESC, category_rank ASC";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("trunc", trunc);
        query.setParameter("tenantId", tenantId);

        if (!"all".equalsIgnoreCase(period)) {
            LocalDate from = computeFromDate(period);
            query.setParameter("fromDate", from);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<Map<String, Object>> results = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tenantId", row[0].toString());
            map.put("periodMonth", row[1].toString());
            map.put("categoryId", ((Number) row[2]).intValue());
            map.put("categoryName", row[3]);
            map.put("actualRevenue", row[4] instanceof BigDecimal bd ? bd.doubleValue() : ((Number) row[4]).doubleValue());
            map.put("predictedRevenue", row[5] instanceof BigDecimal bd ? bd.doubleValue() : ((Number) row[5]).doubleValue());
            map.put("totalUnits", ((Number) row[6]).longValue());
            map.put("categoryRank", ((Number) row[7]).longValue());
            results.add(map);
        }
        return results;
    }

    public DataRangeDto getDataRange(UUID tenantId) {
        String sql = """
            SELECT
                MIN(CASE WHEN is_forecast = false THEN transaction_date END),
                MAX(CASE WHEN is_forecast = false THEN transaction_date END),
                MIN(CASE WHEN is_forecast = true THEN transaction_date END),
                MAX(CASE WHEN is_forecast = true THEN transaction_date END)
            FROM fact_sales_daily
            WHERE tenant_id = :tenantId
            """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);

        Object[] row = (Object[]) query.getSingleResult();

        LocalDate actMin = row[0] != null ? ((Date) row[0]).toLocalDate() : null;
        LocalDate actMax = row[1] != null ? ((Date) row[1]).toLocalDate() : null;
        long actDays = (actMin != null && actMax != null) ? ChronoUnit.DAYS.between(actMin, actMax) + 1 : 0;

        LocalDate fcMin = row[2] != null ? ((Date) row[2]).toLocalDate() : null;
        LocalDate fcMax = row[3] != null ? ((Date) row[3]).toLocalDate() : null;
        long fcDays = (fcMin != null && fcMax != null) ? ChronoUnit.DAYS.between(fcMin, fcMax) + 1 : 0;

        return new DataRangeDto(actMin, actMax, actDays, fcMin, fcMax, fcDays);
    }

    @Transactional
    public void refreshSummaryForTenant(UUID tenantId) {
        log.info("Refreshing sales_insights_summary for tenant {}", tenantId);
        salesInsightRepository.refreshSummaryForTenant(tenantId);
        log.info("Summary refreshed for tenant {}", tenantId);
    }

    private LocalDate computeFromDate(String period) {
        LocalDate now = LocalDate.now();
        return switch (period) {
            case "quarter" -> now.withDayOfMonth(1).minusMonths((now.getMonthValue() - 1) % 3);
            case "year" -> now.withMonth(1).withDayOfMonth(1);
            default -> now.withDayOfMonth(1); // month
        };
    }
}
