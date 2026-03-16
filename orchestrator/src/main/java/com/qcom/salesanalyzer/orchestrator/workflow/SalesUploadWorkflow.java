package com.qcom.salesanalyzer.orchestrator.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface SalesUploadWorkflow {

    @WorkflowMethod
    void processUpload(String jobId, String tenantId, String filePath, String periodType);
}
