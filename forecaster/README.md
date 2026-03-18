# Forecaster Service

The **Forecaster** submits ML forecasting workflows to [Argo Workflows](https://argoproj.github.io/workflows/) running on Kubernetes (Minikube). If Argo is unavailable, it falls back to a local linear forecast.

## Tech Stack

- Java 21, Spring Boot 3.2, Spring Data JPA
- Argo Workflows (Kubernetes)
- Python (XGBoost, Prophet) — runs inside Argo containers
- Lombok, springdoc-openapi (Swagger)

## Port

- **8082** (default)

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/forecast/trigger` | Submit Argo forecast workflow (or local fallback) |
| POST | `/api/forecast/run-local` | Force local linear forecast |
| DELETE | `/api/forecast/{tenantId}` | Clear all forecast data for a tenant |

## Argo Workflow Templates

Located in `argo-workflows/`:

| Template | Algorithm | Description |
|----------|-----------|-------------|
| `sales-forecast-xgboost-workflow-template.yaml` | XGBoost | Gradient boosted trees with time/lag features |
| `sales-forecast-workflow-template.yaml` | Prophet | Facebook Prophet time series model |

### Workflow DAG (4 steps)

1. **extract-actuals** — Pulls actuals from PostgreSQL, base64-encodes CSV
2. **run-forecast** — Runs XGBoost/Prophet, generates predictions for next calendar month
3. **write-results** — Writes forecast rows to `fact_sales_daily` with `is_forecast=true`
4. **notify-callback** — Sends callback to Orchestrator (Temporal signal)
5. **refresh-mv** — Refreshes `mv_final_sales_insights`

### Forecast Horizon

The horizon is dynamically computed as the exact number of days in the next calendar month:

```python
next_month_start = max_date.replace(day=1) + pd.DateOffset(months=1)
horizon = calendar.monthrange(next_month_start.year, next_month_start.month)[1]
```

### Deploy Templates

```bash
kubectl --context minikube apply -f argo-workflows/sales-forecast-xgboost-workflow-template.yaml
kubectl --context minikube apply -f argo-workflows/sales-forecast-workflow-template.yaml
```

## Local Fallback

When Argo is unreachable, the service runs a simple linear moving-average forecast in Java:
- Computes average monthly revenue per category+location from actuals
- Generates 1 month of daily forecast rows distributed evenly

## Configuration

Key properties in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8082 | Service port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/salesanalyzer` | Database URL |
| `app.argo.server-url` | `https://localhost:2746` | Argo server API URL |
| `app.argo.namespace` | `argo` | Kubernetes namespace for Argo |
| `app.argo.workflow-template.prophet` | `sales-forecast-workflow` | Prophet template name |
| `app.argo.workflow-template.xgboost` | `sales-forecast-xgboost-workflow` | XGBoost template name |
| `app.argo.service-account` | `argo-workflow-sa` | K8s service account for workflows |

## Running

```bash
mvn spring-boot:run
# or
mvn package -DskipTests && java -jar target/forecaster-1.0.0-SNAPSHOT.jar
```

Requires Minikube + Argo for full functionality. Works without them (local fallback).

Swagger UI: http://localhost:8082/swagger-ui.html
