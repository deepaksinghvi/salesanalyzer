package com.qcom.salesanalyzer.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ForecastTriggerActivity {

    @ActivityMethod
    String submitForecastRequest(String tenantId, String algorithm, String horizon, String callbackUrl);
}
