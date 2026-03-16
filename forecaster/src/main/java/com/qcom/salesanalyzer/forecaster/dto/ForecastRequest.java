package com.qcom.salesanalyzer.forecaster.dto;

import lombok.Data;

@Data
public class ForecastRequest {
    private String tenantId;
    private String algorithm; // "prophet" (default) or "xgboost"
    private String callbackUrl; // Orchestrator callback URL (optional)
}
