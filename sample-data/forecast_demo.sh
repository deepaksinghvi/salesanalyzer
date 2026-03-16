#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────
# Forecast Demo Script
# Uploads data month-by-month, forecasts, and compares
# actuals against predictions. Press Enter to advance,
# Ctrl+C to stop.
# ──────────────────────────────────────────────────────────
set -euo pipefail

DOCKER_PG="51c780e00abb"
TENANT="e883201e-276b-44ce-bc0f-7c9c365ca301"
LOGIN_EMAIL="admin@acmecorp.com"
LOGIN_PASSWORD="admin"
GATEWAY="http://localhost:8080"
FORECASTER="http://localhost:8082"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ALGO="${1:-xgboost}"  # pass "prophet" or "xgboost" as first arg; default xgboost

# Ordered list of months
MONTHS=(
  "sales_october_2025.csv|Oct 2025"
  "sales_november_2025.csv|Nov 2025"
  "sales_december_2025.csv|Dec 2025"
  "sales_january_2026.csv|Jan 2026"
  "sales_february_2026.csv|Feb 2026"
  "sales_march_2026.csv|Mar 2026"
  "sales_april_2026.csv|Apr 2026"
)

SEED_COUNT=3   # upload first 3 months before first forecast

# ── Helpers ──────────────────────────────────────────────

BOLD="\033[1m"
GREEN="\033[32m"
CYAN="\033[36m"
YELLOW="\033[33m"
RED="\033[31m"
RESET="\033[0m"

banner()  { echo -e "\n${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}"; echo -e "${BOLD}${CYAN}  $1${RESET}"; echo -e "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}"; }
info()    { echo -e "${GREEN}▸ $1${RESET}"; }
warn()    { echo -e "${YELLOW}▸ $1${RESET}"; }
header()  { echo -e "\n${BOLD}$1${RESET}"; }

sql() {
  docker exec "$DOCKER_PG" psql -U postgres -d salesanalyzer -t -A -c "$1" 2>/dev/null
}

sql_pretty() {
  docker exec "$DOCKER_PG" psql -U postgres -d salesanalyzer -c "$1" 2>/dev/null
}

wait_for_enter() {
  echo ""
  echo -e "${BOLD}${YELLOW}  Press ENTER to continue (Ctrl+C to stop)...${RESET}"
  read -r
}

login() {
  TOKEN=$(curl -s "$GATEWAY/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${LOGIN_EMAIL}\",\"password\":\"${LOGIN_PASSWORD}\"}" | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
}

upload_csv() {
  local file="$1"
  curl -s -X POST "$GATEWAY/api/uploads" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@${SCRIPT_DIR}/${file}" \
    -F "periodType=daily" > /dev/null
}

refresh_mv() {
  sql "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_final_sales_insights" > /dev/null 2>&1 || true
}

show_actuals_summary() {
  header "📊 Loaded Actuals:"
  sql_pretty "
    SELECT DATE_TRUNC('month', transaction_date)::date AS month,
           ROUND(SUM(amount)::numeric, 0) AS revenue,
           SUM(units_sold) AS units
    FROM fact_sales_daily
    WHERE tenant_id = '${TENANT}' AND is_forecast = false
    GROUP BY month ORDER BY month;"
}

show_forecast_summary() {
  header "🔮 Current Forecast:"
  sql_pretty "
    SELECT DATE_TRUNC('month', transaction_date)::date AS month,
           ROUND(SUM(amount)::numeric, 0) AS revenue,
           SUM(units_sold) AS units
    FROM fact_sales_daily
    WHERE tenant_id = '${TENANT}' AND is_forecast = true
    GROUP BY month ORDER BY month;"
}

show_comparison() {
  local month_label="$1"
  # Find the latest month that has BOTH actuals and forecasts (the overlap month)
  local overlap_month
  overlap_month=$(sql "
    SELECT TO_CHAR(MAX(m), 'YYYY-MM-DD') FROM (
      SELECT DATE_TRUNC('month', transaction_date) AS m
      FROM fact_sales_daily WHERE tenant_id = '${TENANT}' AND is_forecast = true
      INTERSECT
      SELECT DATE_TRUNC('month', transaction_date) AS m
      FROM fact_sales_daily WHERE tenant_id = '${TENANT}' AND is_forecast = false
    ) sub;" | tr -d ' ')

  if [ -z "$overlap_month" ] || [ "$overlap_month" = "" ]; then
    warn "No overlap month found (no month has both actuals and forecast yet)"
    return
  fi

  header "⚖️  Actual vs Forecast for ${month_label} (${overlap_month:0:7}):"
  sql_pretty "
    SELECT
      c.name AS category,
      ROUND(COALESCE(a.actual_rev, 0)::numeric, 0)   AS actual_revenue,
      ROUND(COALESCE(f.forecast_rev, 0)::numeric, 0)  AS forecast_revenue,
      CASE WHEN COALESCE(f.forecast_rev, 0) > 0
           THEN ROUND(((a.actual_rev - f.forecast_rev) / f.forecast_rev * 100)::numeric, 1)
           ELSE NULL END                               AS error_pct
    FROM dim_categories c
    LEFT JOIN (
      SELECT category_id, SUM(amount) AS actual_rev
      FROM fact_sales_daily
      WHERE tenant_id = '${TENANT}' AND is_forecast = false
        AND DATE_TRUNC('month', transaction_date) = '${overlap_month}'::timestamp
      GROUP BY category_id
    ) a ON c.category_id = a.category_id
    LEFT JOIN (
      SELECT category_id, SUM(amount) AS forecast_rev
      FROM fact_sales_daily
      WHERE tenant_id = '${TENANT}' AND is_forecast = true
        AND DATE_TRUNC('month', transaction_date) = '${overlap_month}'::timestamp
      GROUP BY category_id
    ) f ON c.category_id = f.category_id
    WHERE c.tenant_id = '${TENANT}'
      AND (a.actual_rev IS NOT NULL OR f.forecast_rev IS NOT NULL)
    ORDER BY c.name;"
  # Show totals
  sql_pretty "
    SELECT
      ROUND(SUM(CASE WHEN NOT is_forecast THEN amount ELSE 0 END)::numeric, 0) AS actual_total,
      ROUND(SUM(CASE WHEN is_forecast THEN amount ELSE 0 END)::numeric, 0)     AS forecast_total,
      ROUND(((SUM(CASE WHEN NOT is_forecast THEN amount ELSE 0 END)
            - SUM(CASE WHEN is_forecast THEN amount ELSE 0 END))
            / NULLIF(SUM(CASE WHEN is_forecast THEN amount ELSE 0 END), 0) * 100)::numeric, 1) AS total_error_pct
    FROM fact_sales_daily
    WHERE tenant_id = '${TENANT}'
      AND DATE_TRUNC('month', transaction_date) = '${overlap_month}'::timestamp;"
}

trigger_forecast() {
  info "Triggering ${ALGO} forecast..."
  local result
  result=$(curl -s -X POST "$FORECASTER/api/forecast/trigger" \
    -H "Content-Type: application/json" \
    -d "{\"tenantId\": \"${TENANT}\", \"algorithm\": \"${ALGO}\"}")
  local wf_name
  wf_name=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('workflowName','unknown'))")
  info "Argo workflow: ${wf_name}"

  # Wait for workflow to complete by checking its overall phase
  info "Waiting for Argo workflow to finish..."
  for i in $(seq 1 60); do
    sleep 5
    local phase
    phase=$(minikube kubectl -- get workflow "${wf_name}" -n argo \
      -o jsonpath='{.status.phase}' 2>/dev/null || echo "")
    if [ "$phase" = "Succeeded" ]; then
      echo ""
      info "Workflow completed ✓"
      return 0
    elif [ "$phase" = "Failed" ] || [ "$phase" = "Error" ]; then
      echo ""
      echo -e "${RED}  ✗ Workflow ${phase}${RESET}"
      return 1
    fi
    printf "."
  done
  echo ""
  echo -e "${RED}  ✗ Workflow timed out after 5 minutes${RESET}"
  return 1
}

# ── Main ─────────────────────────────────────────────────

trap 'echo -e "\n${YELLOW}Stopped.${RESET}"; exit 0' INT

banner "Forecast Demo — Algorithm: ${ALGO}"
info "Using sample data from: ${SCRIPT_DIR}"
info "Tenant: ${TENANT}"

# Login
info "Logging in..."
login
info "Authenticated ✓"

# Step 0: Clean slate
banner "Step 0: Resetting all data"
sql "DELETE FROM fact_sales_daily WHERE tenant_id = '${TENANT}'" > /dev/null
refresh_mv
info "All sales data cleared ✓"

# Step 1: Upload seed months
banner "Step 1: Uploading seed data (${SEED_COUNT} months)"
for i in $(seq 0 $((SEED_COUNT - 1))); do
  IFS='|' read -r file label <<< "${MONTHS[$i]}"
  info "Uploading ${label} (${file})..."
  upload_csv "$file"
done
info "Waiting for processing..."
sleep 12
refresh_mv
show_actuals_summary

# Step 2: Initial forecast
banner "Step 2: Running initial ${ALGO} forecast"
trigger_forecast
show_forecast_summary

wait_for_enter

# Step 3+: Iterate through remaining months
for i in $(seq $SEED_COUNT $((${#MONTHS[@]} - 1))); do
  IFS='|' read -r file label <<< "${MONTHS[$i]}"

  banner "Step $((i + 1)): Upload ${label} — Compare actuals vs forecast"

  # The forecast's first month should overlap with this new actual month
  show_comparison "$label"

  info "Uploading ${label} actuals (${file})..."
  upload_csv "$file"
  sleep 10

  # Re-forecast: old predictions for this month are preserved, only future forecasts replaced
  info "Re-running ${ALGO} forecast with new data..."
  trigger_forecast

  show_actuals_summary
  show_forecast_summary

  if [ "$i" -lt "$((${#MONTHS[@]} - 1))" ]; then
    wait_for_enter
  fi
done

banner "Demo Complete"
info "All ${#MONTHS[@]} months uploaded and compared."
info "Final data in database:"
show_actuals_summary
show_forecast_summary
