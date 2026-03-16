#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────
# Clean sales data (actuals + forecasts) for a tenant
#
# Usage:
#   ./clean_data.sh acmecorp          # by company name
#   ./clean_data.sh e883201e-...      # by tenant UUID
#   ./clean_data.sh all               # all tenants
# ──────────────────────────────────────────────────────────
set -euo pipefail

DOCKER_PG="51c780e00abb"

BOLD="\033[1m"
GREEN="\033[32m"
YELLOW="\033[33m"
RED="\033[31m"
RESET="\033[0m"

sql() {
  docker exec "$DOCKER_PG" psql -U postgres -d salesanalyzer -t -A -c "$1" 2>/dev/null
}

sql_pretty() {
  docker exec "$DOCKER_PG" psql -U postgres -d salesanalyzer -c "$1" 2>/dev/null
}

usage() {
  echo -e "${BOLD}Usage:${RESET} $0 <tenant>"
  echo ""
  echo "  <tenant> can be:"
  echo "    - Company name   e.g. acmecorp, qcom"
  echo "    - Tenant UUID    e.g. e883201e-276b-44ce-bc0f-7c9c365ca301"
  echo "    - 'all'          to remove data for ALL tenants"
  echo ""
  echo -e "${BOLD}Available tenants:${RESET}"
  sql_pretty "SELECT tenant_id, company_name FROM dim_tenants ORDER BY company_name;"
  exit 1
}

if [ $# -lt 1 ]; then
  usage
fi

INPUT="$1"

if [ "$(echo "$INPUT" | tr '[:upper:]' '[:lower:]')" = "all" ]; then
  # Show what will be deleted
  echo -e "${BOLD}Data to be deleted (ALL tenants):${RESET}"
  sql_pretty "
    SELECT t.company_name, f.is_forecast,
      COUNT(*) AS rows, ROUND(SUM(f.amount)::numeric, 0) AS total_revenue
    FROM fact_sales_daily f
    JOIN dim_tenants t ON f.tenant_id = t.tenant_id
    GROUP BY t.company_name, f.is_forecast
    ORDER BY t.company_name, f.is_forecast;"

  echo ""
  echo -e "${YELLOW}This will delete ALL sales data (actuals + forecasts) for every tenant.${RESET}"
  read -rp "Are you sure? (y/N): " confirm
  if [ "$(echo "$confirm" | tr '[:upper:]' '[:lower:]')" != "y" ]; then
    echo "Cancelled."
    exit 0
  fi

  deleted=$(sql "DELETE FROM fact_sales_daily; SELECT COUNT(*) FROM fact_sales_daily;" | tail -1)
  sql "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_final_sales_insights" > /dev/null 2>&1 || true
  echo -e "${GREEN}✓ All sales data deleted. MV refreshed.${RESET}"

else
  # Resolve tenant UUID from name or UUID input
  TENANT_ID=""

  # Check if input looks like a UUID
  if [[ "$INPUT" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
    TENANT_ID=$(sql "SELECT tenant_id FROM dim_tenants WHERE tenant_id = '${INPUT}';" | tr -d ' ')
  fi

  # If not found by UUID, try company name (case-insensitive)
  if [ -z "$TENANT_ID" ]; then
    TENANT_ID=$(sql "SELECT tenant_id FROM dim_tenants WHERE LOWER(company_name) = LOWER('${INPUT}');" | tr -d ' ')
  fi

  if [ -z "$TENANT_ID" ]; then
    echo -e "${RED}Error: Tenant '${INPUT}' not found.${RESET}"
    echo ""
    echo -e "${BOLD}Available tenants:${RESET}"
    sql_pretty "SELECT tenant_id, company_name FROM dim_tenants ORDER BY company_name;"
    exit 1
  fi

  COMPANY=$(sql "SELECT company_name FROM dim_tenants WHERE tenant_id = '${TENANT_ID}';" | tr -d ' ')

  # Show what will be deleted
  echo -e "${BOLD}Data to be deleted for ${COMPANY} (${TENANT_ID}):${RESET}"
  sql_pretty "
    SELECT
      is_forecast,
      COUNT(*) AS rows,
      MIN(transaction_date) AS from_date,
      MAX(transaction_date) AS to_date,
      ROUND(SUM(amount)::numeric, 0) AS total_revenue
    FROM fact_sales_daily
    WHERE tenant_id = '${TENANT_ID}'
    GROUP BY is_forecast
    ORDER BY is_forecast;"

  row_count=$(sql "SELECT COUNT(*) FROM fact_sales_daily WHERE tenant_id = '${TENANT_ID}';" | tr -d ' ')
  if [ "$row_count" = "0" ]; then
    echo -e "${YELLOW}No data found for ${COMPANY}. Nothing to delete.${RESET}"
    exit 0
  fi

  echo ""
  read -rp "Delete ${row_count} rows for ${COMPANY}? (y/N): " confirm
  if [ "$(echo "$confirm" | tr '[:upper:]' '[:lower:]')" != "y" ]; then
    echo "Cancelled."
    exit 0
  fi

  sql "DELETE FROM fact_sales_daily WHERE tenant_id = '${TENANT_ID}';" > /dev/null
  sql "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_final_sales_insights" > /dev/null 2>&1 || true
  echo -e "${GREEN}✓ Deleted ${row_count} rows for ${COMPANY}. MV refreshed.${RESET}"
fi
