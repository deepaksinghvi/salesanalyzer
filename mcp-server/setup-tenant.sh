#!/usr/bin/env bash
#
# Setup a new tenant for the SalesAnalyzer MCP server.
#
# Usage:
#   ./mcp-server/setup-tenant.sh admin@newcorp.com mypassword
#   ./mcp-server/setup-tenant.sh admin@newcorp.com              # defaults password to "admin"
#
# What it does:
#   1. Extracts company name from email domain (e.g. admin@newcorp.com -> newcorp)
#   2. Logs in as SuperAdmin to the gateway
#   3. Creates the tenant (if it doesn't already exist)
#   4. Creates the admin user for that tenant
#   5. Verifies login with the new credentials
#   6. Updates .mcp.json so Claude uses the new tenant
#

set -euo pipefail

GATEWAY_URL="${SA_GATEWAY_URL:-http://localhost:8080}"
SUPERADMIN_EMAIL="${SA_SUPERADMIN_EMAIL:-superadmin@qcom.com}"
SUPERADMIN_PASSWORD="${SA_SUPERADMIN_PASSWORD:-admin}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MCP_JSON="$PROJECT_DIR/.mcp.json"

# --- Parse args ---
if [ $# -lt 1 ]; then
    echo "Usage: $0 <email> [password]"
    echo "  email    - e.g. admin@newcorp.com (company name derived from domain)"
    echo "  password - defaults to 'admin'"
    exit 1
fi

EMAIL="$1"
PASSWORD="${2:-admin}"

# Extract company name from email domain (admin@newcorp.com -> newcorp)
DOMAIN="${EMAIL##*@}"
COMPANY="${DOMAIN%%.*}"

echo "==> Setting up tenant: $COMPANY"
echo "    Email:    $EMAIL"
echo "    Gateway:  $GATEWAY_URL"
echo ""

# --- Step 1: Login as SuperAdmin ---
echo "==> Logging in as SuperAdmin..."
LOGIN_RESP=$(curl -sf -X POST "$GATEWAY_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$SUPERADMIN_EMAIL\",\"password\":\"$SUPERADMIN_PASSWORD\"}" 2>&1) || {
    echo "ERROR: Failed to login as SuperAdmin ($SUPERADMIN_EMAIL)."
    echo "       Is the gateway running at $GATEWAY_URL?"
    exit 1
}
SA_TOKEN=$(echo "$LOGIN_RESP" | jq -r '.token')
echo "    SuperAdmin login OK"

# --- Step 2: Create tenant (skip if exists) ---
echo "==> Checking if tenant '$COMPANY' exists..."
TENANTS=$(curl -sf -X GET "$GATEWAY_URL/api/tenants" \
    -H "Authorization: Bearer $SA_TOKEN")
EXISTING_ID=$(echo "$TENANTS" | jq -r ".[] | select(.companyName == \"$COMPANY\") | .tenantId")

if [ -n "$EXISTING_ID" ]; then
    TENANT_ID="$EXISTING_ID"
    echo "    Tenant already exists: $TENANT_ID"
else
    echo "==> Creating tenant '$COMPANY'..."
    CREATE_RESP=$(curl -sf -X POST "$GATEWAY_URL/api/tenants" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $SA_TOKEN" \
        -d "{\"companyName\":\"$COMPANY\",\"subscriptionTier\":\"Enterprise\",\"timezone\":\"UTC\"}")
    TENANT_ID=$(echo "$CREATE_RESP" | jq -r '.tenantId')
    echo "    Tenant created: $TENANT_ID"
fi

# --- Step 3: Create admin user ---
echo "==> Creating user '$EMAIL'..."
# Extract first name from email local part (admin@x.com -> Admin)
LOCAL_PART="${EMAIL%%@*}"
FIRST_NAME="$(echo "$LOCAL_PART" | sed 's/.*/\u&/')"

USER_RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/api/users" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $SA_TOKEN" \
    -d "{\"tenantId\":\"$TENANT_ID\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"Admin\",\"firstName\":\"$FIRST_NAME\",\"lastName\":\"\"}")

if [ "$USER_RESP" = "201" ]; then
    echo "    User created successfully"
elif [ "$USER_RESP" = "409" ] || [ "$USER_RESP" = "500" ]; then
    echo "    User may already exist (HTTP $USER_RESP) — continuing"
else
    echo "    WARNING: Unexpected response creating user (HTTP $USER_RESP)"
fi

# --- Step 4: Verify login ---
echo "==> Verifying login as $EMAIL..."
VERIFY_RESP=$(curl -sf -X POST "$GATEWAY_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" 2>&1) || {
    echo "ERROR: Login verification failed for $EMAIL"
    exit 1
}
VERIFIED_TENANT=$(echo "$VERIFY_RESP" | jq -r '.tenantId')
VERIFIED_ROLE=$(echo "$VERIFY_RESP" | jq -r '.role')
echo "    Login OK — tenant: $VERIFIED_TENANT, role: $VERIFIED_ROLE"

# --- Step 5: Update .mcp.json ---
echo "==> Updating $MCP_JSON..."
cat > "$MCP_JSON" << EOF
{
  "mcpServers": {
    "salesanalyzer": {
      "command": "mcp-server/.venv/bin/python3.13",
      "args": ["mcp-server/server.py"],
      "env": {
        "SA_GATEWAY_URL": "$GATEWAY_URL",
        "SA_EMAIL": "$EMAIL",
        "SA_PASSWORD": "$PASSWORD"
      }
    }
  }
}
EOF
echo "    .mcp.json updated"

echo ""
echo "==> Done! Tenant '$COMPANY' is ready."
echo "    Restart Claude Code to pick up the new MCP config."
