package com.qcom.salesanalyzer.orchestrator.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ForecastTriggerWorkflow {

    @WorkflowMethod
    String triggerForecast(String tenantId, String algorithm, String horizon);

    @SignalMethod
    void forecastCompleted(String status);
}
