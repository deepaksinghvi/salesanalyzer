-- ============================================================
-- SalesAnalyzer Database Schema
-- Multi-tenant Star Schema for Sales Reporting & Forecasting
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- DIMENSION TABLES
-- ============================================================

CREATE TABLE dim_tenants (
    tenant_id   UUID        DEFAULT uuid_generate_v4() PRIMARY KEY,
    company_name        VARCHAR(255) NOT NULL,
    subscription_tier   VARCHAR(50)  NOT NULL DEFAULT 'Basic' CHECK (subscription_tier IN ('Basic', 'Pro', 'Enterprise')),
    timezone            VARCHAR(100) NOT NULL DEFAULT 'UTC',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE dim_users (
    user_id     UUID        DEFAULT uuid_generate_v4() PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES dim_tenants(tenant_id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL DEFAULT 'Viewer' CHECK (role IN ('SuperAdmin', 'Admin', 'Viewer')),
    first_name  VARCHAR(100),
    last_name   VARCHAR(100),
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE dim_categories (
    category_id SERIAL      PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES dim_tenants(tenant_id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    UNIQUE (tenant_id, name)
);

CREATE TABLE dim_locations (
    location_id SERIAL      PRIMARY KEY,
    city        VARCHAR(255) NOT NULL,
    region      VARCHAR(255) NOT NULL,
    UNIQUE (city, region)
);

-- ============================================================
-- FACT TABLE
-- ============================================================

CREATE TABLE fact_sales_daily (
    id              BIGSERIAL   PRIMARY KEY,
    transaction_date DATE        NOT NULL,
    tenant_id       UUID        NOT NULL REFERENCES dim_tenants(tenant_id) ON DELETE CASCADE,
    category_id     INT         NOT NULL REFERENCES dim_categories(category_id),
    location_id     INT         NOT NULL REFERENCES dim_locations(location_id),
    amount          DECIMAL(15,2) NOT NULL,
    units_sold      INT         NOT NULL DEFAULT 0,
    is_forecast     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fact_sales_tenant_date ON fact_sales_daily(tenant_id, transaction_date);
CREATE INDEX idx_fact_sales_category    ON fact_sales_daily(category_id);
CREATE INDEX idx_fact_sales_location    ON fact_sales_daily(location_id);
CREATE INDEX idx_fact_sales_forecast    ON fact_sales_daily(is_forecast);

-- ============================================================
-- UPLOAD TRACKING TABLE
-- ============================================================

CREATE TABLE upload_jobs (
    job_id          UUID        DEFAULT uuid_generate_v4() PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES dim_tenants(tenant_id) ON DELETE CASCADE,
    uploaded_by     UUID        NOT NULL REFERENCES dim_users(user_id),
    file_name       VARCHAR(500) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    period_type     VARCHAR(20) NOT NULL CHECK (period_type IN ('daily', 'monthly', 'quarterly', 'yearly')),
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    rows_processed  INT         DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- ============================================================
-- MATERIALIZED VIEW
-- ============================================================

CREATE MATERIALIZED VIEW mv_final_sales_insights AS
SELECT
    fsd.tenant_id,
    DATE_TRUNC('month', fsd.transaction_date)::date AS period_month,
    fsd.category_id,
    dc.name                                      AS category_name,
    SUM(CASE WHEN fsd.is_forecast = FALSE THEN fsd.amount ELSE 0 END) AS actual_revenue,
    SUM(CASE WHEN fsd.is_forecast = TRUE  THEN fsd.amount ELSE 0 END) AS predicted_revenue,
    SUM(fsd.units_sold)                          AS total_units,
    RANK() OVER (
        PARTITION BY fsd.tenant_id, DATE_TRUNC('month', fsd.transaction_date)::date
        ORDER BY SUM(fsd.amount) DESC
    ) AS category_rank
FROM fact_sales_daily fsd
JOIN dim_categories dc ON fsd.category_id = dc.category_id
GROUP BY 1, 2, 3, 4;

CREATE UNIQUE INDEX idx_mv_final_sales_insights ON mv_final_sales_insights(tenant_id, period_month, category_id);

-- ============================================================
-- SEED DATA: qcom tenant + superadmin user
-- ============================================================

INSERT INTO dim_tenants (tenant_id, company_name, subscription_tier, timezone)
VALUES ('00000000-0000-0000-0000-000000000001', 'qcom', 'Enterprise', 'UTC');

-- Password: admin  (bcrypt hash)
INSERT INTO dim_users (user_id, tenant_id, email, password_hash, role, first_name, last_name)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    '00000000-0000-0000-0000-000000000001',
    'superadmin@qcom.com',
    '$2a$10$0qIHoY0qFck.8FUEoLJ0De3Ab83dlABkUIGol5OZ7Q9jQU2h5iqZm',
    'SuperAdmin',
    'Super',
    'Admin'
);
