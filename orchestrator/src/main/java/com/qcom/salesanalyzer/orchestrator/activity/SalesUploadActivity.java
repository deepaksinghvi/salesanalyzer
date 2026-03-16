package com.qcom.salesanalyzer.orchestrator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SalesUploadActivity {

    @ActivityMethod
    void updateJobStatus(String jobId, String status, String errorMessage);

    @ActivityMethod
    int parseCsvAndInsert(String jobId, String tenantId, String filePath);
}
