package com.qcom.salesanalyzer.gateway.repository;

import com.qcom.salesanalyzer.gateway.entity.UploadJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, UUID> {
    List<UploadJob> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<UploadJob> findByStatus(String status);
}
