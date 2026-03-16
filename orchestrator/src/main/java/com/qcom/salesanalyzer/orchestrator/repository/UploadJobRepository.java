package com.qcom.salesanalyzer.orchestrator.repository;

import com.qcom.salesanalyzer.orchestrator.entity.UploadJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, UUID> {
}
