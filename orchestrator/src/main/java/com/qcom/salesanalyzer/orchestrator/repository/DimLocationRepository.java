package com.qcom.salesanalyzer.orchestrator.repository;

import com.qcom.salesanalyzer.orchestrator.entity.DimLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DimLocationRepository extends JpaRepository<DimLocation, Integer> {
    Optional<DimLocation> findByCityAndRegion(String city, String region);
}
