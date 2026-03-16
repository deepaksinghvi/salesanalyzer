package com.qcom.salesanalyzer.gateway.repository;

import com.qcom.salesanalyzer.gateway.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByCompanyName(String companyName);
    boolean existsByCompanyName(String companyName);
}
