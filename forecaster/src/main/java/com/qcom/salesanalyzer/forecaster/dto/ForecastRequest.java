package com.qcom.salesanalyzer.forecaster.dto;

import lombok.Data;

@Data
public class ForecastRequest {
    private String tenantId;
    private String algorithm; // "prophet" (default) or "xgboost"
    private String horizon;   // "1w","2w","3w","1m","2m","1q","1y" (default: "1m")
    private String callbackUrl; // Orchestrator callback URL (optional)
}
