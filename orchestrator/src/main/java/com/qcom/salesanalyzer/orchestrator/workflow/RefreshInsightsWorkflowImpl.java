package com.qcom.salesanalyzer.orchestrator.workflow;

import com.qcom.salesanalyzer.orchestrator.activity.RefreshInsightsActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class RefreshInsightsWorkflowImpl implements RefreshInsightsWorkflow {

    private final RefreshInsightsActivity refreshActivity = Workflow.newActivityStub(
            RefreshInsightsActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .build())
                    .build());

    @Override
    public void refreshInsights() {
        refreshActivity.refreshMaterializedView();
    }
}
