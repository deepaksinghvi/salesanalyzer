#!/usr/bin/env bash
# =============================================================================
# SalesAnalyzer — One-time Setup Script (macOS)
# =============================================================================
# Installs all prerequisites and prepares the project for development.
#
# Usage:
#   ./setup.sh           # install everything
#   ./setup.sh --check   # just check what's installed/missing
#
# After setup, run:
#   ./dev.sh             # start all infrastructure + services
#
# What this script installs (via Homebrew):
#   - Docker Desktop
#   - Java 21 (Temurin)
#   - Maven
#   - Node.js 20
#   - Python 3.13
#   - minikube
#   - kubectl
#   - jq
#
# It also:
#   - Creates the Python virtual environment for the MCP server
#   - Installs npm dependencies for the UI
#   - Builds all Java services
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ok()   { echo -e "  ${GREEN}✓${NC} $*"; }
miss() { echo -e "  ${RED}✗${NC} $*"; }
skip() { echo -e "  ${YELLOW}→${NC} $*"; }
log()  { echo -e "\n${GREEN}==>${NC} $*"; }

CHECK_ONLY=false
[[ "${1:-}" == "--check" ]] && CHECK_ONLY=true

MISSING=()

# =============================================================================
# Check / Install Homebrew
# =============================================================================
check_brew() {
    if command -v brew &>/dev/null; then
        ok "Homebrew $(brew --version | head -1 | awk '{print $2}')"
        return 0
    else
        miss "Homebrew not found"
        MISSING+=(brew)
        return 1
    fi
}

install_brew() {
    log "Installing Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    # Add to path for Apple Silicon
    if [[ -f /opt/homebrew/bin/brew ]]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
}

# =============================================================================
# Check / Install individual tools
# =============================================================================
check_docker() {
    if command -v docker &>/dev/null && docker info &>/dev/null; then
        ok "Docker $(docker --version | awk '{print $3}' | tr -d ',')"
        return 0
    elif command -v docker &>/dev/null; then
        miss "Docker installed but not running — start Docker Desktop"
        MISSING+=(docker-running)
        return 1
    else
        miss "Docker not found"
        MISSING+=(docker)
        return 1
    fi
}

check_java() {
    if command -v java &>/dev/null; then
        local ver
        ver=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}')
        local major
        major=$(echo "$ver" | cut -d. -f1)
        if [[ "$major" -ge 21 ]]; then
            ok "Java $ver"
            return 0
        else
            miss "Java $ver found but need 21+ "
            MISSING+=(java)
            return 1
        fi
    else
        miss "Java not found"
        MISSING+=(java)
        return 1
    fi
}

check_maven() {
    if command -v mvn &>/dev/null; then
        ok "Maven $(mvn --version 2>/dev/null | head -1 | awk '{print $3}')"
        return 0
    else
        miss "Maven not found"
        MISSING+=(maven)
        return 1
    fi
}

check_node() {
    if command -v node &>/dev/null; then
        local ver
        ver=$(node --version | tr -d 'v')
        local major
        major=$(echo "$ver" | cut -d. -f1)
        if [[ "$major" -ge 20 ]]; then
            ok "Node.js $ver"
            return 0
        else
            miss "Node.js $ver found but need 20+"
            MISSING+=(node)
            return 1
        fi
    else
        miss "Node.js not found"
        MISSING+=(node)
        return 1
    fi
}

check_python() {
    local py=""
    if command -v python3.13 &>/dev/null; then
        py="python3.13"
    elif command -v python3 &>/dev/null; then
        local ver
        ver=$(python3 --version 2>&1 | awk '{print $2}')
        local minor
        minor=$(echo "$ver" | cut -d. -f2)
        if [[ "$minor" -ge 13 ]]; then
            py="python3"
        fi
    fi

    if [[ -n "$py" ]]; then
        ok "Python $($py --version 2>&1 | awk '{print $2}')"
        return 0
    else
        miss "Python 3.13+ not found"
        MISSING+=(python)
        return 1
    fi
}

check_minikube() {
    if command -v minikube &>/dev/null; then
        ok "minikube $(minikube version --short 2>/dev/null | tr -d 'v')"
        return 0
    else
        miss "minikube not found"
        MISSING+=(minikube)
        return 1
    fi
}

check_kubectl() {
    if command -v kubectl &>/dev/null; then
        ok "kubectl $(kubectl version --client -o json 2>/dev/null | jq -r '.clientVersion.gitVersion' 2>/dev/null || echo '?')"
        return 0
    else
        miss "kubectl not found"
        MISSING+=(kubectl)
        return 1
    fi
}

check_jq() {
    if command -v jq &>/dev/null; then
        ok "jq $(jq --version 2>/dev/null | tr -d 'jq-')"
        return 0
    else
        miss "jq not found"
        MISSING+=(jq)
        return 1
    fi
}

# =============================================================================
# Run checks
# =============================================================================
echo ""
echo "============================================"
echo "  SalesAnalyzer — Prerequisites Check"
echo "============================================"
echo ""

check_brew || true
check_docker || true
check_java || true
check_maven || true
check_node || true
check_python || true
check_minikube || true
check_kubectl || true
check_jq || true

# Check project-level setup
echo ""
echo "  Project setup:"
if [[ -d "$ROOT/mcp-server/.venv" ]] && [[ -f "$ROOT/mcp-server/.venv/bin/python3" ]]; then
    ok "MCP server venv"
else
    miss "MCP server venv not created"
    MISSING+=(mcp-venv)
fi

if [[ -d "$ROOT/ui/node_modules" ]]; then
    ok "UI node_modules"
else
    miss "UI node_modules not installed"
    MISSING+=(ui-deps)
fi

echo ""

# =============================================================================
# Check-only mode
# =============================================================================
if $CHECK_ONLY; then
    if [[ ${#MISSING[@]} -eq 0 ]]; then
        echo -e "${GREEN}All prerequisites are installed!${NC}"
        echo "Run ./dev.sh to start the project."
    else
        echo -e "${YELLOW}Missing: ${MISSING[*]}${NC}"
        echo "Run ./setup.sh to install everything."
    fi
    exit 0
fi

# =============================================================================
# Install missing prerequisites
# =============================================================================
if [[ ${#MISSING[@]} -eq 0 ]]; then
    echo -e "${GREEN}All prerequisites already installed!${NC}"
    echo ""
    echo "Run ./dev.sh to start the project."
    exit 0
fi

echo "Will install: ${MISSING[*]}"
echo ""
read -rp "Continue? [Y/n] " confirm
if [[ "${confirm:-Y}" =~ ^[Nn] ]]; then
    echo "Aborted."
    exit 0
fi

# Install Homebrew first if needed
if [[ " ${MISSING[*]} " =~ " brew " ]]; then
    install_brew
fi

# Now install via Homebrew
for item in "${MISSING[@]}"; do
    case "$item" in
        brew)
            ;; # already handled above
        docker)
            log "Installing Docker Desktop..."
            brew install --cask docker
            echo ""
            skip "Docker Desktop installed — please open it from Applications and start it."
            skip "Then re-run ./setup.sh to continue."
            ;;
        docker-running)
            skip "Docker is installed but not running. Start Docker Desktop and re-run ./setup.sh"
            ;;
        java)
            log "Installing Java 21 (Temurin)..."
            brew install --cask temurin@21
            # Set JAVA_HOME for this session
            if [[ -d "/Library/Java/JavaVirtualMachines/temurin-21.jdk" ]]; then
                export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
                export PATH="$JAVA_HOME/bin:$PATH"
            fi
            ok "Java 21 installed"
            ;;
        maven)
            log "Installing Maven..."
            brew install maven
            ok "Maven installed"
            ;;
        node)
            log "Installing Node.js 20..."
            brew install node@20
            brew link node@20 --overwrite --force 2>/dev/null || true
            ok "Node.js installed"
            ;;
        python)
            log "Installing Python 3.13..."
            brew install python@3.13
            ok "Python 3.13 installed"
            ;;
        minikube)
            log "Installing minikube..."
            brew install minikube
            ok "minikube installed"
            ;;
        kubectl)
            log "Installing kubectl..."
            brew install kubectl
            ok "kubectl installed"
            ;;
        jq)
            log "Installing jq..."
            brew install jq
            ok "jq installed"
            ;;
        mcp-venv)
            log "Setting up MCP server Python virtual environment..."
            local py_cmd="python3.13"
            if ! command -v python3.13 &>/dev/null; then
                py_cmd="python3"
            fi
            $py_cmd -m venv "$ROOT/mcp-server/.venv"
            "$ROOT/mcp-server/.venv/bin/pip" install -q -r "$ROOT/mcp-server/requirements.txt"
            ok "MCP server venv created and dependencies installed"
            ;;
        ui-deps)
            log "Installing UI dependencies..."
            (cd "$ROOT/ui" && npm install --silent)
            ok "UI dependencies installed"
            ;;
    esac
done

# =============================================================================
# Project setup (always run if deps are now present)
# =============================================================================

# MCP venv
if [[ ! -d "$ROOT/mcp-server/.venv" ]]; then
    log "Setting up MCP server Python virtual environment..."
    local_py="python3.13"
    if ! command -v python3.13 &>/dev/null; then
        local_py="python3"
    fi
    $local_py -m venv "$ROOT/mcp-server/.venv"
    "$ROOT/mcp-server/.venv/bin/pip" install -q -r "$ROOT/mcp-server/requirements.txt"
    ok "MCP server venv ready"
fi

# UI deps
if [[ ! -d "$ROOT/ui/node_modules" ]]; then
    log "Installing UI dependencies..."
    (cd "$ROOT/ui" && npm install --silent)
    ok "UI dependencies installed"
fi

# Build Java services
log "Building Java services..."
for svc in gateway orchestrator forecaster; do
    echo -n "  Building $svc... "
    if mvn -f "$ROOT/$svc/pom.xml" clean package -DskipTests -q 2>/dev/null; then
        echo -e "${GREEN}OK${NC}"
    else
        echo -e "${RED}FAILED${NC} (check $ROOT/$svc for errors)"
    fi
done

echo ""
echo "============================================"
echo -e "  ${GREEN}Setup complete!${NC}"
echo "============================================"
echo ""
echo "  Next steps:"
echo "    1. ./dev.sh                          # start everything"
echo "    2. Open http://localhost:5173         # UI"
echo "    3. Login: superadmin@qcom.com / admin"
echo ""
echo "  To set up the MCP server for a tenant:"
echo "    ./mcp-server/setup-tenant.sh admin@yourcompany.com"
echo ""
