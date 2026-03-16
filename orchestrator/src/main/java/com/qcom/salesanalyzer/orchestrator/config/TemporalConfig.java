package com.qcom.salesanalyzer.orchestrator.config;

import com.qcom.salesanalyzer.orchestrator.activity.ForecastTriggerActivityImpl;
import com.qcom.salesanalyzer.orchestrator.activity.RefreshInsightsActivityImpl;
import com.qcom.salesanalyzer.orchestrator.activity.SalesUploadActivityImpl;
import com.qcom.salesanalyzer.orchestrator.workflow.ForecastTriggerWorkflowImpl;
import com.qcom.salesanalyzer.orchestrator.workflow.RefreshInsightsWorkflowImpl;
import com.qcom.salesanalyzer.orchestrator.workflow.SalesUploadWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class TemporalConfig {

    @Value("${temporal.connection.target:localhost:7233}")
    private String temporalTarget;

    @Value("${temporal.namespace:default}")
    private String temporalNamespace;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(temporalTarget)
                        .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(
                stubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(temporalNamespace)
                        .build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient,
                                       SalesUploadActivityImpl salesUploadActivity,
                                       RefreshInsightsActivityImpl refreshInsightsActivity,
                                       ForecastTriggerActivityImpl forecastTriggerActivity) {
        WorkerFactory factory = WorkerFactory.newInstance(workflowClient);

        Worker uploadWorker = factory.newWorker("SALES_UPLOAD_TASK_QUEUE");
        uploadWorker.registerWorkflowImplementationTypes(SalesUploadWorkflowImpl.class);
        uploadWorker.registerActivitiesImplementations(salesUploadActivity);

        Worker refreshWorker = factory.newWorker("SALES_REFRESH_TASK_QUEUE");
        refreshWorker.registerWorkflowImplementationTypes(RefreshInsightsWorkflowImpl.class);
        refreshWorker.registerActivitiesImplementations(refreshInsightsActivity);

        Worker forecastWorker = factory.newWorker("FORECAST_TRIGGER_TASK_QUEUE");
        forecastWorker.registerWorkflowImplementationTypes(ForecastTriggerWorkflowImpl.class);
        forecastWorker.registerActivitiesImplementations(forecastTriggerActivity);

        try {
            factory.start();
            log.info("Temporal WorkerFactory started with 3 workers targeting {}", temporalTarget);
        } catch (Exception e) {
            log.warn("Temporal not available at {} — workers not started. Will retry on next request. Error: {}",
                    temporalTarget, e.getMessage());
        }
        return factory;
    }
}
