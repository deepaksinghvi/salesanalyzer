package com.qcom.salesanalyzer.gateway.repository;

import com.qcom.salesanalyzer.gateway.entity.SalesInsight;
import com.qcom.salesanalyzer.gateway.entity.SalesInsightId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SalesInsightRepository extends JpaRepository<SalesInsight, SalesInsightId> {
    List<SalesInsight> findByTenantIdOrderByPeriodMonthDescCategoryRankAsc(UUID tenantId);
    List<SalesInsight> findByTenantIdAndPeriodMonthGreaterThanEqualOrderByPeriodMonthDescCategoryRankAsc(UUID tenantId, LocalDate from);
    List<SalesInsight> findByTenantIdAndPeriodMonthBetweenOrderByPeriodMonthDescCategoryRankAsc(UUID tenantId, LocalDate from, LocalDate to);

    @Modifying
    @Query(value = "DELETE FROM sales_insights_summary WHERE tenant_id = :tenantId", nativeQuery = true)
    void deleteByTenantId(UUID tenantId);

    @Modifying
    @Query(value = """
        INSERT INTO sales_insights_summary (tenant_id, period_month, category_id, category_name, actual_revenue, predicted_revenue, total_units, category_rank, updated_at)
        SELECT
            sub.tenant_id, sub.period_month, sub.category_id, sub.category_name,
            sub.actual_revenue, sub.predicted_revenue, sub.total_units,
            RANK() OVER (PARTITION BY sub.tenant_id, sub.period_month ORDER BY (sub.actual_revenue + sub.predicted_revenue) DESC),
            NOW()
        FROM (
            SELECT
                fsd.tenant_id,
                CAST(DATE_TRUNC('month', fsd.transaction_date) AS date) AS period_month,
                fsd.category_id,
                dc.name AS category_name,
                SUM(CASE WHEN fsd.is_forecast = FALSE THEN fsd.amount ELSE 0 END) AS actual_revenue,
                SUM(CASE WHEN fsd.is_forecast = TRUE  THEN fsd.amount ELSE 0 END) AS predicted_revenue,
                SUM(fsd.units_sold) AS total_units
            FROM fact_sales_daily fsd
            JOIN dim_categories dc ON fsd.category_id = dc.category_id
            WHERE fsd.tenant_id = :tenantId
            GROUP BY fsd.tenant_id, CAST(DATE_TRUNC('month', fsd.transaction_date) AS date), fsd.category_id, dc.name
        ) sub
        ON CONFLICT (tenant_id, period_month, category_id) DO UPDATE SET
            category_name = EXCLUDED.category_name,
            actual_revenue = EXCLUDED.actual_revenue,
            predicted_revenue = EXCLUDED.predicted_revenue,
            total_units = EXCLUDED.total_units,
            category_rank = EXCLUDED.category_rank,
            updated_at = NOW()
        """, nativeQuery = true)
    void refreshSummaryForTenant(UUID tenantId);
}
