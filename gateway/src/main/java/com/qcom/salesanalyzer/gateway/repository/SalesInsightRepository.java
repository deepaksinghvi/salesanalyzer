package com.qcom.salesanalyzer.gateway.repository;

import com.qcom.salesanalyzer.gateway.entity.SalesInsight;
import com.qcom.salesanalyzer.gateway.entity.SalesInsightId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SalesInsightRepository extends JpaRepository<SalesInsight, SalesInsightId> {
    List<SalesInsight> findByTenantIdOrderByPeriodMonthDescCategoryRankAsc(UUID tenantId);
    List<SalesInsight> findByTenantIdAndPeriodMonthGreaterThanEqualOrderByPeriodMonthDescCategoryRankAsc(UUID tenantId, OffsetDateTime from);
}
