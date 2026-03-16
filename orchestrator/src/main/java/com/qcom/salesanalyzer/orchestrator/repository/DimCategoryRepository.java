package com.qcom.salesanalyzer.orchestrator.repository;

import com.qcom.salesanalyzer.orchestrator.entity.DimCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DimCategoryRepository extends JpaRepository<DimCategory, Integer> {
    Optional<DimCategory> findByTenantIdAndName(UUID tenantId, String name);
}
