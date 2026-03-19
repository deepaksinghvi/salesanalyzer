package com.qcom.salesanalyzer.orchestrator.repository;

import com.qcom.salesanalyzer.orchestrator.entity.FactSalesDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface FactSalesDailyRepository extends JpaRepository<FactSalesDaily, Long> {

    @Modifying
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_final_sales_insights", nativeQuery = true)
    void refreshMaterializedView();

    @Query(value = "SELECT COUNT(*) FROM fact_sales_daily WHERE tenant_id = :tenantId AND is_forecast = false", nativeQuery = true)
    long countActualsByTenant(UUID tenantId);

    @Modifying
    @Query(value = "DELETE FROM fact_sales_daily WHERE tenant_id = :tenantId AND is_forecast = true " +
            "AND transaction_date BETWEEN :fromDate AND :toDate", nativeQuery = true)
    int deleteOverlappingForecasts(UUID tenantId, LocalDate fromDate, LocalDate toDate);
}
