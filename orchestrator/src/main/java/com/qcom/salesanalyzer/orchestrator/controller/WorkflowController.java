package com.qcom.salesanalyzer.orchestrator.controller;

import com.qcom.salesanalyzer.orchestrator.workflow.SalesUploadWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowClient workflowClient;

    @PostMapping("/process-upload")
    public ResponseEntity<Map<String, String>> processUpload(@RequestBody Map<String, String> payload) {
        String jobId     = payload.get("jobId");
        String tenantId  = payload.get("tenantId");
        String filePath  = payload.get("filePath");
        String periodType = payload.get("periodType");

        log.info("Starting SalesUploadWorkflow for jobId={}, tenantId={}", jobId, tenantId);

        SalesUploadWorkflow workflow = workflowClient.newWorkflowStub(
                SalesUploadWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("SALES_UPLOAD_TASK_QUEUE")
                        .setWorkflowId("upload-" + jobId)
                        .build());

        WorkflowClient.start(workflow::processUpload, jobId, tenantId, filePath, periodType);

        return ResponseEntity.ok(Map.of(
                "workflowId", "upload-" + jobId,
                "status", "STARTED"
        ));
    }
}
