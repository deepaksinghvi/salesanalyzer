# Orchestrator Service

The **Orchestrator** manages all asynchronous workflows using [Temporal](https://temporal.io). It processes CSV uploads, refreshes materialized views, and coordinates forecast pipelines.

## Tech Stack

- Java 21, Spring Boot 3.2, Temporal Java SDK
- Spring Data JPA (PostgreSQL)
- Lombok, springdoc-openapi (Swagger)

## Port

- **8081** (default)

## Temporal Workflows

| Workflow | Task Queue | Trigger | Description |
|----------|-----------|---------|-------------|
| `SalesUploadWorkflow` | `SALES_UPLOAD_TASK_QUEUE` | On CSV upload | Parses CSV, inserts data, refreshes MV |
| `ForecastTriggerWorkflow` | `FORECAST_TRIGGER_TASK_QUEUE` | Manual or weekly cron | Calls Forecaster, waits for Argo callback via signal |
| `RefreshInsightsWorkflow` | `SALES_REFRESH_TASK_QUEUE` | Hourly cron | Refreshes `mv_final_sales_insights` |

## Temporal Activities

| Activity | Description |
|----------|-------------|
| `SalesUploadActivity` | CSV parsing, dimension resolution, fact table insertion |
| `ForecastTriggerActivity` | HTTP call to Forecaster service with callback URL |
| `RefreshInsightsActivity` | `REFRESH MATERIALIZED VIEW CONCURRENTLY` |

## Signal/Callback Flow

The `ForecastTriggerWorkflow` uses Temporal signals for async completion:

```
Orchestrator → Forecaster → Argo workflow starts
                                  ↓ (on completion)
              Argo → POST /api/workflows/forecast-callback?workflowId=...
                                  ↓
              Orchestrator receives signal → workflow completes
```

The callback URL uses `host.minikube.internal:8081` for Argo pods to reach the Orchestrator.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/workflows/process-upload` | Start CSV upload workflow |
| POST | `/api/workflows/trigger-forecast` | Start forecast workflow |
| POST | `/api/workflows/forecast-callback` | Argo completion callback (signals workflow) |

## Scheduled Jobs

| Schedule | Workflow | Description |
|----------|----------|-------------|
| Every hour | `RefreshInsightsWorkflow` | Refreshes materialized view |
| Monday 00:00 | `ForecastTriggerWorkflow` | Weekly XGBoost forecast for all tenants |

## Configuration

Key properties in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8081 | Service port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/salesanalyzer` | Database URL |
| `temporal.connection.target` | `localhost:7233` | Temporal server address |
| `temporal.namespace` | `default` | Temporal namespace |
| `app.forecaster.base-url` | `http://localhost:8082` | Forecaster service URL |

## Running

```bash
mvn spring-boot:run
# or
mvn package -DskipTests && java -jar target/orchestrator-1.0.0-SNAPSHOT.jar
```

Requires Temporal server to be running on port 7233.

Swagger UI: http://localhost:8081/swagger-ui.html
