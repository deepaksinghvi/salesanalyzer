package com.qcom.salesanalyzer.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateTenantRequest {
    @NotBlank
    private String companyName;
    private String subscriptionTier = "Basic";
    private String timezone = "UTC";
}
