package com.qcom.salesanalyzer.gateway.controller;

import com.qcom.salesanalyzer.gateway.dto.CreateTenantRequest;
import com.qcom.salesanalyzer.gateway.entity.Tenant;
import com.qcom.salesanalyzer.gateway.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    private boolean isSuperAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SuperAdmin"));
    }

    @GetMapping
    public ResponseEntity<List<Tenant>> getAllTenants() {
        if (!isSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<Tenant> getTenant(@PathVariable UUID tenantId) {
        if (!isSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(tenantService.getTenantById(tenantId));
    }

    @PostMapping
    public ResponseEntity<Tenant> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        if (!isSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.createTenant(request));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<Tenant> updateTenant(@PathVariable UUID tenantId,
                                                @Valid @RequestBody CreateTenantRequest request) {
        if (!isSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(tenantService.updateTenant(tenantId, request));
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> deleteTenant(@PathVariable UUID tenantId) {
        if (!isSuperAdmin()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.noContent().build();
    }
}
