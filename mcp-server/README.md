# MCP Server

A [Model Context Protocol](https://modelcontextprotocol.io) server that connects Claude (Desktop or Code) to the SalesAnalyzer platform. Enables natural language interaction with sales data, forecasting, and uploads.

## Tech Stack

- Python 3.13, MCP SDK (FastMCP)
- httpx (HTTP client)

## Tools

| Tool | Description |
|------|-------------|
| `get_sales_insights` | Query revenue by month/category with period filter (month/quarter/year/all) |
| `get_top_categories` | Rank categories by actual revenue |
| `run_forecast` | Trigger XGBoost or Prophet forecast via Temporal |
| `clear_forecast` | Remove all forecast data (keeps actuals) |
| `upload_sales_csv` | Upload a CSV file for processing |
| `get_connection_info` | Show current auth status and connected user |

Tenant context is implicit from the configured login credentials. No tenant IDs are exposed.

Role-based access is enforced — Viewer users get friendly error messages for write operations.

## Setup

```bash
cd mcp-server
/opt/homebrew/bin/python3.13 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

## Configuration

Environment variables (set in `.env` or `.mcp.json`):

| Variable | Default | Description |
|----------|---------|-------------|
| `SA_GATEWAY_URL` | `http://localhost:8080` | Gateway API base URL |
| `SA_EMAIL` | `admin@acmecorp.com` | Login email |
| `SA_PASSWORD` | `admin` | Login password |

## Usage with Claude Code

The project root `.mcp.json` configures this server automatically. Start Claude Code from the `salesanalyzer/` directory:

```bash
cd /path/to/salesanalyzer
claude
```

Then ask Claude things like:
- "What are my sales insights for this quarter?"
- "Show me the top categories by revenue"
- "Run a forecast using XGBoost"
- "Upload the January sales CSV from sample-data/"

## Usage with Claude Desktop

Add to `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "salesanalyzer": {
      "command": "/path/to/salesanalyzer/mcp-server/.venv/bin/python3.13",
      "args": ["/path/to/salesanalyzer/mcp-server/server.py"],
      "env": {
        "SA_GATEWAY_URL": "http://localhost:8080",
        "SA_EMAIL": "admin@acmecorp.com",
        "SA_PASSWORD": "admin"
      }
    }
  }
}
```

## Running Standalone

```bash
.venv/bin/python3.13 server.py
```

This starts the MCP server on stdio (used by Claude clients).
