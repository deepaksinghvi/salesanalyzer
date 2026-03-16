package com.qcom.salesanalyzer.orchestrator.controller;

import com.qcom.salesanalyzer.orchestrator.workflow.ForecastTriggerWorkflow;
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

    @PostMapping("/trigger-forecast")
    public ResponseEntity<Map<String, String>> triggerForecast(@RequestBody Map<String, String> payload) {
        String tenantId  = payload.getOrDefault("tenantId", "ALL_TENANTS");
        String algorithm = payload.getOrDefault("algorithm", "xgboost");

        String workflowId = "forecast-" + tenantId.substring(0, Math.min(8, tenantId.length()))
                + "-" + System.currentTimeMillis();

        log.info("Starting ForecastTriggerWorkflow: workflowId={}, tenantId={}, algorithm={}", workflowId, tenantId, algorithm);

        ForecastTriggerWorkflow workflow = workflowClient.newWorkflowStub(
                ForecastTriggerWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("FORECAST_TRIGGER_TASK_QUEUE")
                        .setWorkflowId(workflowId)
                        .build());

        WorkflowClient.start(workflow::triggerForecast, tenantId, algorithm);

        return ResponseEntity.ok(Map.of(
                "workflowId", workflowId,
                "tenantId", tenantId,
                "algorithm", algorithm,
                "status", "STARTED"
        ));
    }

    @PostMapping("/forecast-callback")
    public ResponseEntity<Map<String, String>> forecastCallback(
            @RequestParam String workflowId,
            @RequestBody Map<String, String> payload) {

        String status = payload.getOrDefault("status", "COMPLETED");
        log.info("Forecast callback received: workflowId={}, status={}", workflowId, status);

        try {
            ForecastTriggerWorkflow workflow = workflowClient.newWorkflowStub(
                    ForecastTriggerWorkflow.class, workflowId);
            workflow.forecastCompleted(status);
            log.info("Signal sent to workflow {}: status={}", workflowId, status);
            return ResponseEntity.ok(Map.of("workflowId", workflowId, "signaled", "true"));
        } catch (Exception e) {
            log.error("Failed to signal workflow {}: {}", workflowId, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("workflowId", workflowId, "signaled", "false", "error", e.getMessage()));
        }
    }
}
