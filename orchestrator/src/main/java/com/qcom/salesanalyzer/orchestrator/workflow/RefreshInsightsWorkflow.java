package com.qcom.salesanalyzer.orchestrator.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface RefreshInsightsWorkflow {

    @WorkflowMethod
    void refreshInsights();
}
