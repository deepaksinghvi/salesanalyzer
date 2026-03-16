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

    List<FactSalesDaily> findByTenantIdAndTransactionDateAfterAndIsForecastTrue(UUID tenantId, LocalDate date);

    @Modifying
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_final_sales_insights", nativeQuery = true)
    void refreshMaterializedView();
}
