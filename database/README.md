# Database

PostgreSQL schema definitions for the SalesAnalyzer platform.

## Schema

Multi-tenant star schema for sales reporting and forecasting.

### Dimension Tables

| Table | Description |
|-------|-------------|
| `dim_tenants` | Organizations (tenant_id, company_name, subscription_tier, timezone) |
| `dim_users` | Users per tenant (email, password_hash, role: SuperAdmin/Admin/Viewer) |
| `dim_categories` | Product categories per tenant |
| `dim_locations` | City + region lookup |

### Fact Table

| Table | Description |
|-------|-------------|
| `fact_sales_daily` | Sales transactions and forecasts. The `is_forecast` flag distinguishes actuals from predictions. |

### Upload Tracking

| Table | Description |
|-------|-------------|
| `upload_jobs` | CSV upload job status (PENDING → PROCESSING → COMPLETED/FAILED) |

### Materialized View

| View | Description |
|------|-------------|
| `mv_final_sales_insights` | Monthly aggregation of actual vs predicted revenue by tenant, category. Includes category rank. |

The MV uses `period_month` as `date` type (not `timestamptz`) to avoid timezone issues.

## Migrations

Located in `migrations/V1__init_schema.sql`. This file is also copied to the Gateway's Flyway migration path.

**Note**: The Gateway service manages schema via Flyway on startup. These files are the canonical source — edit here first, then sync to `gateway/src/main/resources/db/migration/`.

## Seed Data

The migration seeds:
- Tenant: `qcom` (Enterprise tier)
- SuperAdmin: `superadmin@qcom.com` / `admin`

Additional tenants (e.g., `acmecorp`) are created via the API or Swagger UI.

## Indexes

```
idx_fact_sales_tenant_date  — (tenant_id, transaction_date)
idx_fact_sales_category     — (category_id)
idx_fact_sales_location     — (location_id)
idx_fact_sales_forecast     — (is_forecast)
idx_mv_final_sales_insights — (tenant_id, period_month, category_id) UNIQUE
```
