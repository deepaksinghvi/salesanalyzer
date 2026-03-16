package com.qcom.salesanalyzer.gateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dim_tenants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "subscription_tier", nullable = false)
    private String subscriptionTier;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (subscriptionTier == null) subscriptionTier = "Basic";
        if (timezone == null) timezone = "UTC";
    }
}
