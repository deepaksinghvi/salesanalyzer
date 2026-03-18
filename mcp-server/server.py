"""
SalesAnalyzer MCP Server
Exposes sales insights, forecasting, and data upload tools to Claude.
Tenant context is derived from the configured login credentials.
"""

import os
import json
import httpx
from mcp.server.fastmcp import FastMCP

# -- Configuration via environment variables --
GATEWAY_URL = os.environ.get("SA_GATEWAY_URL", "http://localhost:8080")
EMAIL = os.environ.get("SA_EMAIL", "admin@acmecorp.com")
PASSWORD = os.environ.get("SA_PASSWORD", "admin")

mcp = FastMCP("salesanalyzer", instructions="""
You are connected to a Sales Analyzer application. You can query sales insights,
trigger ML-based revenue forecasts, and upload sales CSV data.
The tenant is determined by the configured user credentials — do not ask for tenant IDs.
""")

# -- Auth state --
_auth: dict = {}


def _login() -> dict:
    """Authenticate and cache token + tenant info."""
    if _auth.get("token"):
        return _auth
    resp = httpx.post(f"{GATEWAY_URL}/api/auth/login", json={"email": EMAIL, "password": PASSWORD}, timeout=10)
    resp.raise_for_status()
    data = resp.json()
    _auth["token"] = data["token"]
    _auth["tenant_id"] = data["tenantId"]
    _auth["email"] = data["email"]
    _auth["role"] = data["role"]
    return _auth


def _headers() -> dict:
    auth = _login()
    return {"Authorization": f"Bearer {auth['token']}"}


def _fmt_currency(n: float) -> str:
    if abs(n) >= 1_000_000:
        return f"${n / 1_000_000:.1f}M"
    if abs(n) >= 1_000:
        return f"${n / 1_000:.1f}K"
    return f"${n:.2f}"


# ============================================================
# TOOLS
# ============================================================


@mcp.tool()
def get_sales_insights(period: str = "all") -> str:
    """Get sales insights (actual revenue, forecasted revenue, units sold) grouped by month and category.

    Args:
        period: Time period filter. One of: "month" (current month), "quarter" (current quarter),
                "year" (current year), "all" (all time). Defaults to "all".
    """
    auth = _login()
    resp = httpx.get(
        f"{GATEWAY_URL}/api/insights/{auth['tenant_id']}",
        params={"period": period},
        headers=_headers(),
        timeout=15,
    )
    resp.raise_for_status()
    insights = resp.json()

    if not insights:
        return "No sales data found for this tenant."

    # Build a structured summary
    by_month: dict = {}
    for row in insights:
        month = row["periodMonth"][:7]  # "2025-10"
        if month not in by_month:
            by_month[month] = {"actual": 0, "forecast": 0, "units": 0, "categories": {}}
        actual = row.get("actualRevenue") or 0
        forecast = row.get("predictedRevenue") or 0
        units = row.get("totalUnits") or 0
        by_month[month]["actual"] += actual
        by_month[month]["forecast"] += forecast
        by_month[month]["units"] += units
        cat = row.get("categoryName", "Unknown")
        if cat not in by_month[month]["categories"]:
            by_month[month]["categories"][cat] = {"actual": 0, "forecast": 0, "units": 0}
        by_month[month]["categories"][cat]["actual"] += actual
        by_month[month]["categories"][cat]["forecast"] += forecast
        by_month[month]["categories"][cat]["units"] += units

    lines = [f"Sales insights ({period}) - {len(insights)} records across {len(by_month)} months\n"]
    total_actual = 0
    total_forecast = 0
    total_units = 0

    for month in sorted(by_month):
        m = by_month[month]
        total_actual += m["actual"]
        total_forecast += m["forecast"]
        total_units += m["units"]
        label = "ACTUAL" if m["actual"] > 0 else "FORECAST"
        lines.append(f"## {month} [{label}]")
        if m["actual"] > 0:
            lines.append(f"  Revenue: {_fmt_currency(m['actual'])}  |  Units: {m['units']:,}")
        if m["forecast"] > 0:
            lines.append(f"  Forecast: {_fmt_currency(m['forecast'])}")
        for cat in sorted(m["categories"]):
            c = m["categories"][cat]
            parts = []
            if c["actual"] > 0:
                parts.append(f"actual={_fmt_currency(c['actual'])}")
            if c["forecast"] > 0:
                parts.append(f"forecast={_fmt_currency(c['forecast'])}")
            if c["units"] > 0:
                parts.append(f"units={c['units']:,}")
            lines.append(f"    - {cat}: {', '.join(parts)}")
        lines.append("")

    lines.append("---")
    lines.append(f"Total actual revenue: {_fmt_currency(total_actual)}")
    lines.append(f"Total forecasted revenue: {_fmt_currency(total_forecast)}")
    lines.append(f"Total units sold: {total_units:,}")
    return "\n".join(lines)


@mcp.tool()
def get_top_categories(period: str = "all", limit: int = 5) -> str:
    """Get the top-performing categories by revenue.

    Args:
        period: Time period filter. One of: "month", "quarter", "year", "all". Defaults to "all".
        limit: Number of top categories to return. Defaults to 5.
    """
    auth = _login()
    resp = httpx.get(
        f"{GATEWAY_URL}/api/insights/{auth['tenant_id']}",
        params={"period": period},
        headers=_headers(),
        timeout=15,
    )
    resp.raise_for_status()
    insights = resp.json()

    if not insights:
        return "No sales data found."

    # Aggregate by category
    by_cat: dict = {}
    for row in insights:
        cat = row.get("categoryName", "Unknown")
        actual = row.get("actualRevenue") or 0
        units = row.get("totalUnits") or 0
        if cat not in by_cat:
            by_cat[cat] = {"revenue": 0, "units": 0}
        by_cat[cat]["revenue"] += actual
        by_cat[cat]["units"] += units

    ranked = sorted(by_cat.items(), key=lambda x: x[1]["revenue"], reverse=True)[:limit]
    lines = [f"Top {min(limit, len(ranked))} categories by actual revenue ({period}):\n"]
    for i, (cat, data) in enumerate(ranked, 1):
        lines.append(f"  {i}. {cat}: {_fmt_currency(data['revenue'])} ({data['units']:,} units)")
    return "\n".join(lines)


@mcp.tool()
def run_forecast(algorithm: str = "xgboost") -> str:
    """Trigger an ML-based revenue forecast. This starts a Temporal workflow that runs
    an Argo/XGBoost or Prophet pipeline to predict next month's revenue.

    Args:
        algorithm: Forecasting algorithm to use. Either "xgboost" or "prophet". Defaults to "xgboost".
    """
    auth = _login()
    if auth.get("role") == "Viewer":
        return "Error: Viewer role cannot trigger forecasts. Contact an Admin."

    resp = httpx.post(
        f"{GATEWAY_URL}/api/forecast/trigger",
        json={"tenantId": auth["tenant_id"], "algorithm": algorithm},
        headers=_headers(),
        timeout=30,
    )
    resp.raise_for_status()
    data = resp.json()
    wf_id = data.get("workflowId", "unknown")
    return (
        f"Forecast triggered successfully.\n"
        f"  Algorithm: {algorithm}\n"
        f"  Workflow ID: {wf_id}\n"
        f"  Status: {data.get('status', 'STARTED')}\n\n"
        f"The forecast runs asynchronously (typically 2-5 minutes). "
        f"Use get_sales_insights() after it completes to see the forecasted data."
    )


@mcp.tool()
def clear_forecast() -> str:
    """Clear all forecasted data for the current tenant. This removes predicted revenue
    but keeps actual sales data intact."""
    auth = _login()
    if auth.get("role") == "Viewer":
        return "Error: Viewer role cannot clear forecasts. Contact an Admin."

    resp = httpx.delete(
        f"{GATEWAY_URL}/api/forecast/{auth['tenant_id']}",
        headers=_headers(),
        timeout=15,
    )
    resp.raise_for_status()
    return "Forecast data cleared successfully. Only actual sales data remains."


@mcp.tool()
def upload_sales_csv(file_path: str, period_type: str = "daily") -> str:
    """Upload a sales CSV file for processing. The file will be parsed and inserted
    into the sales database, then the insights materialized view is refreshed.

    Args:
        file_path: Absolute path to the CSV file on the local filesystem.
        period_type: Granularity of the data. One of: "daily", "monthly", "quarterly", "yearly".
                     Defaults to "daily".
    """
    auth = _login()
    if auth.get("role") == "Viewer":
        return "Error: Viewer role cannot upload data. Contact an Admin."

    file_path = os.path.expanduser(file_path)
    if not os.path.isfile(file_path):
        return f"Error: File not found: {file_path}"

    filename = os.path.basename(file_path)
    with open(file_path, "rb") as f:
        resp = httpx.post(
            f"{GATEWAY_URL}/api/uploads",
            files={"file": (filename, f, "text/csv")},
            data={"periodType": period_type},
            headers=_headers(),
            timeout=60,
        )
    resp.raise_for_status()
    data = resp.json()
    return (
        f"Upload successful.\n"
        f"  File: {data.get('fileName', filename)}\n"
        f"  Job ID: {data.get('jobId', 'unknown')}\n"
        f"  Status: {data.get('status', 'PENDING')}\n"
        f"  Message: {data.get('message', '')}\n\n"
        f"The file is being processed by the orchestrator. "
        f"Use get_sales_insights() after processing completes to see the updated data."
    )


@mcp.tool()
def get_connection_info() -> str:
    """Show the current connection status and authenticated user info."""
    try:
        auth = _login()
        return (
            f"Connected to SalesAnalyzer\n"
            f"  Gateway: {GATEWAY_URL}\n"
            f"  User: {auth['email']}\n"
            f"  Role: {auth['role']}\n"
            f"  Tenant ID: {auth['tenant_id']}"
        )
    except Exception as e:
        return f"Not connected. Error: {e}"


if __name__ == "__main__":
    mcp.run()
