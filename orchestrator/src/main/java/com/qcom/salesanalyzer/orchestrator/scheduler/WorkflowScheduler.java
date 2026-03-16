package com.qcom.salesanalyzer.orchestrator.scheduler;

import com.qcom.salesanalyzer.orchestrator.workflow.ForecastTriggerWorkflow;
import com.qcom.salesanalyzer.orchestrator.workflow.RefreshInsightsWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowScheduler {

    private final WorkflowClient workflowClient;

    @Scheduled(cron = "0 0 * * * *")
    public void scheduleDailyRefresh() {
        log.info("Triggering daily materialized view refresh...");
        RefreshInsightsWorkflow workflow = workflowClient.newWorkflowStub(
                RefreshInsightsWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("SALES_REFRESH_TASK_QUEUE")
                        .setWorkflowId("daily-refresh-" + System.currentTimeMillis())
                        .build());
        WorkflowClient.start(workflow::refreshInsights);
    }

    @Scheduled(cron = "0 0 0 * * MON")
    public void scheduleWeeklyForecast() {
        log.info("Triggering weekly forecast workflow...");
        ForecastTriggerWorkflow workflow = workflowClient.newWorkflowStub(
                ForecastTriggerWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("FORECAST_TRIGGER_TASK_QUEUE")
                        .setWorkflowId("weekly-forecast-" + System.currentTimeMillis())
                        .build());
        WorkflowClient.start(workflow::triggerForecast, "ALL_TENANTS", "xgboost");
    }
}
