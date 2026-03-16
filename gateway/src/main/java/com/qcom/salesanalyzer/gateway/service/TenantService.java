package com.qcom.salesanalyzer.gateway.service;

import com.qcom.salesanalyzer.gateway.dto.CreateTenantRequest;
import com.qcom.salesanalyzer.gateway.entity.Tenant;
import com.qcom.salesanalyzer.gateway.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;

    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    public Tenant getTenantById(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found: " + tenantId));
    }

    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsByCompanyName(request.getCompanyName())) {
            throw new RuntimeException("Tenant with company name already exists: " + request.getCompanyName());
        }
        Tenant tenant = Tenant.builder()
                .companyName(request.getCompanyName())
                .subscriptionTier(request.getSubscriptionTier())
                .timezone(request.getTimezone())
                .build();
        return tenantRepository.save(tenant);
    }

    @Transactional
    public Tenant updateTenant(UUID tenantId, CreateTenantRequest request) {
        Tenant tenant = getTenantById(tenantId);
        tenant.setCompanyName(request.getCompanyName());
        tenant.setSubscriptionTier(request.getSubscriptionTier());
        tenant.setTimezone(request.getTimezone());
        return tenantRepository.save(tenant);
    }

    @Transactional
    public void deleteTenant(UUID tenantId) {
        tenantRepository.deleteById(tenantId);
    }
}
