package com.qcom.salesanalyzer.forecaster.repository;

import com.qcom.salesanalyzer.forecaster.entity.FactSalesDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface FactSalesDailyRepository extends JpaRepository<FactSalesDaily, Long> {

    @Query("SELECT f FROM FactSalesDaily f WHERE f.tenantId = :tenantId AND f.isForecast = false ORDER BY f.transactionDate ASC")
    List<FactSalesDaily> findActualsByTenant(UUID tenantId);

    void deleteByTenantIdAndIsForecastTrue(UUID tenantId);

    @Modifying
    @Query(value = "DELETE FROM fact_sales_daily f "
            + "WHERE f.tenant_id = :tenantId AND f.is_forecast = true "
            + "AND DATE_TRUNC('month', f.transaction_date) NOT IN ("
            + "  SELECT DISTINCT DATE_TRUNC('month', a.transaction_date) "
            + "  FROM fact_sales_daily a "
            + "  WHERE a.tenant_id = :tenantId AND a.is_forecast = false"
            + ")", nativeQuery = true)
    void deleteFutureForecastsByTenant(UUID tenantId);

    long countByTenantIdAndIsForecastTrue(UUID tenantId);

    List<FactSalesDaily> findByTenantIdAndTransactionDateAfterAndIsForecastTrue(UUID tenantId, LocalDate date);

    @Modifying
    @Query(value = "DELETE FROM fact_sales_daily "
            + "WHERE tenant_id = :tenantId AND is_forecast = true "
            + "AND transaction_date BETWEEN :fromDate AND :toDate", nativeQuery = true)
    void deleteForecastsByTenantAndDateRange(UUID tenantId, LocalDate fromDate, LocalDate toDate);

    @Modifying
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_final_sales_insights", nativeQuery = true)
    void refreshMaterializedView();
}
