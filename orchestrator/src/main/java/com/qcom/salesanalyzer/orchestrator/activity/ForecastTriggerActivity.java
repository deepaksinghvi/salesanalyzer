package com.qcom.salesanalyzer.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ForecastTriggerActivity {

    @ActivityMethod
    void submitForecastRequest(String tenantId);
}
