package com.qcom.salesanalyzer.orchestrator.workflow;

import com.qcom.salesanalyzer.orchestrator.activity.ForecastTriggerActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class ForecastTriggerWorkflowImpl implements ForecastTriggerWorkflow {

    private final ForecastTriggerActivity forecastActivity = Workflow.newActivityStub(
            ForecastTriggerActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .build())
                    .build());

    @Override
    public void triggerWeeklyForecast() {
        forecastActivity.submitForecastRequest("ALL_TENANTS");
    }
}
