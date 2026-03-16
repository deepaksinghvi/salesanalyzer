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

    private String completionStatus = null;

    @Override
    public String triggerForecast(String tenantId, String algorithm) {
        String workflowId = Workflow.getInfo().getWorkflowId();
        String callbackUrl = "http://host.minikube.internal:8081/api/workflows/forecast-callback";

        String argoWorkflowName = forecastActivity.submitForecastRequest(tenantId, algorithm,
                callbackUrl + "?workflowId=" + workflowId);

        // Wait for Argo to call back (up to 15 minutes)
        boolean signaled = Workflow.await(Duration.ofMinutes(15), () -> completionStatus != null);

        if (!signaled) {
            return "TIMEOUT:argo=" + argoWorkflowName;
        }
        return completionStatus + ":argo=" + argoWorkflowName;
    }

    @Override
    public void forecastCompleted(String status) {
        this.completionStatus = status;
    }
}
