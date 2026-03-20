-- ============================================================
-- Replace materialized view with a summary table for scalable
-- multi-tenant incremental updates.
-- ============================================================

CREATE TABLE sales_insights_summary (
    tenant_id       UUID        NOT NULL,
    period_month    DATE        NOT NULL,
    category_id     INT         NOT NULL REFERENCES dim_categories(category_id),
    category_name   VARCHAR(255) NOT NULL,
    actual_revenue  DECIMAL(15,2) NOT NULL DEFAULT 0,
    predicted_revenue DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_units     BIGINT      NOT NULL DEFAULT 0,
    category_rank   BIGINT      NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, period_month, category_id)
);

CREATE INDEX idx_sis_tenant ON sales_insights_summary(tenant_id);
CREATE INDEX idx_sis_tenant_month ON sales_insights_summary(tenant_id, period_month);

-- Backfill from existing materialized view
INSERT INTO sales_insights_summary (tenant_id, period_month, category_id, category_name, actual_revenue, predicted_revenue, total_units, category_rank)
SELECT tenant_id, period_month, category_id, category_name, actual_revenue, predicted_revenue, total_units, category_rank
FROM mv_final_sales_insights
ON CONFLICT DO NOTHING;
