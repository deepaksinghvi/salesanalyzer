# SalesAnalyzer

A multi-tenant **Sales Reporting and Forecasting** platform built by **qcom**.

---

## Architecture Overview

```
┌──────────────────┐     ┌──────────────────────────────────────────────┐
│  Claude Desktop  │     │              React UI (port 3000)            │
│  (AI Assistant)  │     │    Login · Dashboard · Upload · Tenants      │
└────────┬─────────┘     └─────────────── ───┬──────────────────────────┘
         │ MCP (stdio)                       │ REST (JWT)
┌────────▼─────────┐                         │
│   MCP Server     │                         │
│  (Python/FastMCP)│────────────────────────┐│
│  Tools:          │                        ││
│  · insights      │                        ││
│  · forecast      │                        ││
│  · upload        │                        ││
└──────────────────┘                        ││
                                            ││
┌───────────────────────────────────────────▼▼─────────────┐
│                Gateway Service (port 8080)               │
│           Auth · CSV Upload → /tmp · Tenant/User CRUD    │
└──────┬────────────────────────────────┬──────────────────┘
       │ notify                         │ REST
┌──────▼──────────────┐       ┌─────────▼──────────────────┐
│ Orchestrator Service│       │   Forecaster Service       │
│     (port 8081)     │       │      (port 8082)           │
│  Temporal Workflows │──────▶│  Argo Workflow trigger     │
│  · process-upload   │       │  + local linear fallback   │
│  · daily refresh    │       └─────────────┬──────────────┘
│  · weekly forecast  │                     │
└──────────┬──────────┘                     │ submit
           │                     ┌──────────▼──────────────┐
           │                     │  Argo Workflows (k8s)   │
           │                     │  · XGBoost pipeline     │
           │                     │  · Prophet pipeline     │
           │                     └──────────┬──────────────┘
           │                                │
┌──────────▼────────────────────────────────▼────────────────┐
│                  PostgreSQL (port 5432)                    │
│  dim_tenants · dim_users · dim_categories · dim_locations  │
│  fact_sales_daily · upload_jobs                            │
│  mv_final_sales_insights (Materialized View)               │
└────────────────────────────────────────────────────────────┘
```

---

## Services

| Service | Port | Tech | Responsibility |
|---|---|---|---|
| `gateway` | 8080 | Spring Boot 3 | JWT auth, CSV upload, tenant/user CRUD |
| `orchestrator` | 8081 | Spring Boot 3 + Temporal | CSV processing workflow, daily MV refresh, weekly forecast trigger |
| `forecaster` | 8082 | Spring Boot 3 | Argo Workflow submission (XGBoost/Prophet), local linear fallback |
| `mcp-server` | stdio | Python 3.13 + FastMCP | MCP server for Claude integration |
| `ui` | 3000 | React + Vite + Tailwind | Dashboard, upload, tenant/user management |

---

## Database Schema

```
dim_tenants          → Organizations (multi-tenant root)
dim_users            → Users per tenant (SuperAdmin / Admin / Viewer)
dim_categories       → Product categories per tenant
dim_locations        → City + Region lookup
fact_sales_daily     → Sales facts (actuals + forecasts via is_forecast flag)
upload_jobs          → CSV upload tracking
mv_final_sales_insights → Materialized view: monthly actual vs forecast by category
```

**Default seed data:**
- Tenant: `qcom` (Enterprise tier)
- SuperAdmin: `superadmin@qcom.com` / `admin`

---

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 20+
- Python 3.13+ (for MCP server)
- PostgreSQL 16 (local or Docker)
- Docker + Docker Compose (for full stack)
- Temporal CLI (for local worker dev)

---

## Development Script (`dev.sh`)

A convenience script at the project root to build, start, stop, and clean all services.

```bash
# Start (stop + build + run) all services
./dev.sh

# Start only specific services
./dev.sh gateway forecaster

# Stop all services
./dev.sh stop

# Stop specific services
./dev.sh stop gateway ui

# mvn clean all Java services (useful before git commit)
./dev.sh clean

# mvn clean specific Java service(s)
./dev.sh clean gateway forecaster
```

**Services:** `gateway`, `orchestrator`, `forecaster`, `ui`  
**Logs:** written to `/tmp/sa-{service}.log` for each running service.

---

## API Documentation (Swagger UI)

All three backend services expose Swagger UI when running locally. No authentication required.

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| Gateway | http://localhost:8080/swagger-ui.html | http://localhost:8080/api-docs |
| Orchestrator | http://localhost:8081/swagger-ui.html | http://localhost:8081/api-docs |
| Forecaster | http://localhost:8082/swagger-ui.html | http://localhost:8082/api-docs |

---

## Local Development (without Docker)

### 1. Start PostgreSQL

```bash
# Using Docker just for Postgres:
docker run -d \
  --name salesanalyzer-pg \
  -e POSTGRES_DB=salesanalyzer \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine
```

### 2. Start Temporal (dev server)

```bash
# Install Temporal CLI first: https://docs.temporal.io/cli
temporal server start-dev --port 7233
```

### 3. Run Gateway (port 8080)

```bash
cd gateway
mvn spring-boot:run
```

Flyway runs on startup and applies `V1__init_schema.sql` automatically.  
Swagger UI: http://localhost:8080/swagger-ui.html

### 4. Run Orchestrator (port 8081)

```bash
cd orchestrator
mvn spring-boot:run
```

Swagger UI: http://localhost:8081/swagger-ui.html

### 5. Run Forecaster (port 8082)

```bash
cd forecaster
mvn spring-boot:run
```

Swagger UI: http://localhost:8082/swagger-ui.html

### 6. Run UI (port 5173 in dev)

```bash
cd ui
npm install
npm run dev
```

Open: http://localhost:5173  
Login: `superadmin@qcom.com` / `admin`

> **Tip:** Use `./dev.sh` from the project root to start all services at once instead of running each manually.

---

## Full Stack with Docker Compose

```bash
cd docker
docker compose up --build
```

| Service | URL |
|---|---|
| UI | http://localhost:3000 |
| Gateway API | http://localhost:8080 |
| Orchestrator | http://localhost:8081 |
| Forecaster | http://localhost:8082 |
| Temporal UI | http://localhost:8088 |

---

## CSV Upload Format

```csv
tenant_id,transaction_date,category_name,city,region,total_revenue,units_sold
QUICK-9923,2026-03-15,Electronics,San Francisco,CA,4500.50,12
QUICK-9923,2026-03-15,Clothing,New York,NY,1200.00,8
```

- Uploaded via `POST /api/uploads` (multipart)
- File saved to `/tmp/salesanalyzer/`
- Gateway notifies Orchestrator → Temporal workflow starts
- CSV rows parsed, categories/locations resolved (or created), inserted into `fact_sales_daily`
- Materialized view `mv_final_sales_insights` refreshed automatically

---

## Temporal Workflows

| Workflow | Task Queue | Trigger |
|---|---|---|
| `SalesUploadWorkflow` | `SALES_UPLOAD_TASK_QUEUE` | On CSV upload |
| `RefreshInsightsWorkflow` | `SALES_REFRESH_TASK_QUEUE` | Hourly (cron) |
| `ForecastTriggerWorkflow` | `FORECAST_TRIGGER_TASK_QUEUE` | Every Monday midnight (cron) |

---

## Forecaster — Argo Workflow

The Argo `WorkflowTemplate` is at:
```
forecaster/argo-workflows/sales-forecast-workflow-template.yaml
```

Apply to a Kubernetes cluster with Argo Workflows installed:
```bash
kubectl apply -f forecaster/argo-workflows/sales-forecast-workflow-template.yaml
```

Two workflow templates are available:

| Template | Algorithm |
|----------|-----------|
| `sales-forecast-xgboost-workflow-template.yaml` | XGBoost (default) |
| `sales-forecast-workflow-template.yaml` | Prophet |

The workflow runs a **3-step DAG**:
1. `run-forecast` — single step that extracts actuals from PostgreSQL, runs XGBoost/Prophet, and writes forecast rows to `fact_sales_daily` with `is_forecast=true`
2. `refresh-mv` — refreshes the `mv_final_sales_insights` materialized view
3. `notify-callback` — sends callback to Orchestrator (Temporal signal)

Supported forecast horizons: `1w`, `2w`, `3w` (weekly), `1m`, `2m` (monthly), `1q` (quarterly), `1y` (yearly).

If Argo is not available, the Forecaster service falls back to a **local linear moving-average** forecast.

---

## API Reference

### Auth
| Method | Endpoint | Body |
|---|---|---|
| POST | `/api/auth/login` | `{ email, password }` |

### Tenants (SuperAdmin only)
| Method | Endpoint |
|---|---|
| GET | `/api/tenants` |
| POST | `/api/tenants` |
| PUT | `/api/tenants/{id}` |
| DELETE | `/api/tenants/{id}` |

### Users (Admin+)
| Method | Endpoint |
|---|---|
| GET | `/api/users/tenant/{tenantId}` |
| POST | `/api/users` |
| DELETE | `/api/users/{userId}` |

### Upload
| Method | Endpoint | Body |
|---|---|---|
| POST | `/api/uploads` | `multipart: file, periodType` |

### Forecast
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/forecast/trigger` | Trigger Argo workflow (falls back to local) |
| POST | `/api/forecast/run-local` | Force local linear forecast |
| DELETE | `/api/forecast/{tenantId}` | Clear all forecast rows for a tenant |

> Full interactive API docs available at the [Swagger UIs](#api-documentation-swagger-ui) above.

### Users (SuperAdmin)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/users` | All users across all tenants |
| PUT | `/api/users/{userId}/password` | Reset a user's password |

---

## Roles

| Role | Capabilities |
|---|---|
| `SuperAdmin` | Full access: manage all tenants, users, data |
| `Admin` | Manage users within own tenant, upload data |
| `Viewer` | View dashboard only |

---

## MCP Server (Claude Integration)

The `mcp-server/` directory contains a Python MCP server that lets Claude interact with SalesAnalyzer via natural language. Configured in `.mcp.json` at the project root.

**Tools:** `get_sales_insights`, `get_top_categories`, `run_forecast`, `clear_forecast`, `upload_sales_csv`, `get_connection_info`

### Quick Setup for a New Tenant

```bash
# Creates the tenant, admin user, and updates .mcp.json in one step
./mcp-server/setup-tenant.sh admin@newcorp.com

# With a custom password
./mcp-server/setup-tenant.sh admin@newcorp.com mypassword
```

The script derives the company name from the email domain (e.g. `admin@newcorp.com` → `newcorp`), creates the tenant and user via the gateway API, and updates `.mcp.json` so Claude uses the new tenant. Restart Claude Code after running.

See [mcp-server/README.md](mcp-server/README.md) for more details.

---

## Results Comparison (May 2026 Forecast)

| Category | Actuals (Apr) | Prophet | XGBoost |
|---|---|---|---|
| Electronics | $144,797 | $138,519 | $137,901 |
| Sports | $74,216 | $96,524 | $79,036 |
| Clothing | $38,140 | $36,288 | $34,746 |
| Home & Garden | $25,997 | $20,438 | $28,467 |
| **Total** | **$283,150** | **$291,769** | **$280,150** |

Both algorithms produce forecasts in line with actual monthly revenue ranges.