package com.qcom.salesanalyzer.orchestrator.workflow;

import com.qcom.salesanalyzer.orchestrator.activity.RefreshInsightsActivity;
import com.qcom.salesanalyzer.orchestrator.activity.SalesUploadActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class SalesUploadWorkflowImpl implements SalesUploadWorkflow {

    private final SalesUploadActivity uploadActivity = Workflow.newActivityStub(
            SalesUploadActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .build())
                    .build());

    private final RefreshInsightsActivity refreshActivity = Workflow.newActivityStub(
            RefreshInsightsActivity.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue("SALES_REFRESH_TASK_QUEUE")
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .build())
                    .build());

    @Override
    public void processUpload(String jobId, String tenantId, String filePath, String periodType) {
        uploadActivity.updateJobStatus(jobId, "PROCESSING", null);
        try {
            int rowsInserted = uploadActivity.parseCsvAndInsert(jobId, tenantId, filePath);
            uploadActivity.updateJobStatus(jobId, "COMPLETED", null);
            refreshActivity.refreshSummaryForTenant(tenantId);
        } catch (Exception e) {
            uploadActivity.updateJobStatus(jobId, "FAILED", e.getMessage());
            throw e;
        }
    }
}
