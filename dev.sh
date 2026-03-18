#!/bin/zsh
# =============================================================================
# SalesAnalyzer — Full Local Dev Environment
# =============================================================================
# Usage:
#   ./dev.sh                        # start everything (infra + all services)
#   ./dev.sh services               # start only Java services + UI (skip infra)
#   ./dev.sh gateway forecaster     # start only named services (skip infra)
#   ./dev.sh infra                  # start only infrastructure (DB, Temporal, Minikube, Argo)
#   ./dev.sh stop                   # stop all services (not infra)
#   ./dev.sh stop gateway ui        # stop only named services
#   ./dev.sh stop all               # stop everything (services + infra)
#   ./dev.sh clean                  # mvn clean all Java services
#   ./dev.sh clean gateway          # mvn clean only named Java service(s)
#   ./dev.sh status                 # show status of everything
#
# Infrastructure:
#   PostgreSQL + Temporal  — via docker compose (docker/docker-compose.infra.yml)
#   Minikube + Argo        — managed directly (namespace, CRDs, templates, port-forward)
#
# Data is persisted in a named Docker volume (postgres_data).
# Stopping/restarting does NOT lose data. Only `docker volume rm` would.

ROOT="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$ROOT/docker/docker-compose.infra.yml"

# ── Java / Maven setup ─────────────────────────────────────────────────────────
[[ -f "$HOME/.zshrc" ]] && source "$HOME/.zshrc" 2>/dev/null 1>/dev/null || true
[[ -f "$HOME/.zprofile" ]] && source "$HOME/.zprofile" 2>/dev/null 1>/dev/null || true

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v /usr/libexec/java_home &>/dev/null; then
    export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
  else
    JAVA_BIN="$(command -v java 2>/dev/null)"
    if [[ -n "$JAVA_BIN" ]]; then
      export JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    fi
  fi
fi
[[ -n "${JAVA_HOME:-}" ]] && export PATH="$JAVA_HOME/bin:$PATH"

# ── Constants ─────────────────────────────────────────────────────────────────
ARGO_VERSION="v3.5.5"
ARGO_INSTALL_URL="https://github.com/argoproj/argo-workflows/releases/download/${ARGO_VERSION}/install.yaml"
KUBECTL_CONTEXT="minikube"
ARGO_NAMESPACE="argo"

ALL_SERVICES=(gateway orchestrator forecaster ui)
JAVA_SERVICES=(gateway orchestrator forecaster)

# ── Helpers ────────────────────────────────────────────────────────────────────
log()  { echo "[$(date '+%H:%M:%S')] $*"; }
warn() { echo "[$(date '+%H:%M:%S')] WARNING: $*"; }
die()  { echo "[$(date '+%H:%M:%S')] ERROR: $*" >&2; exit 1; }

svc_port() {
  case $1 in
    gateway)      echo 8080 ;;
    orchestrator) echo 8081 ;;
    forecaster)   echo 8082 ;;
    ui)           echo 5173 ;;
  esac
}
svc_dir() { echo "$ROOT/$1"; }
svc_log() { echo "/tmp/sa-$1.log"; }

is_valid_service() {
  for s in "${ALL_SERVICES[@]}"; do [[ "$s" == "$1" ]] && return 0; done
  return 1
}

kctl() {
  kubectl --context "$KUBECTL_CONTEXT" "$@"
}

wait_for() {
  local desc=$1 cmd=$2 timeout=${3:-60}
  local i=0
  log "Waiting for $desc..."
  while ! eval "$cmd" >/dev/null 2>&1; do
    sleep 2; (( i += 2 ))
    if (( i >= timeout )); then
      warn "$desc not ready after ${timeout}s"
      return 1
    fi
  done
  log "$desc is ready"
  return 0
}

# =============================================================================
# INFRASTRUCTURE
# =============================================================================

start_compose_infra() {
  log "── PostgreSQL + Temporal (docker compose) ──"
  if ! command -v docker &>/dev/null; then
    die "docker not found in PATH"
  fi
  if [[ ! -f "$COMPOSE_FILE" ]]; then
    die "Compose file not found: $COMPOSE_FILE"
  fi

  docker compose -f "$COMPOSE_FILE" up -d

  # Wait for postgres to be healthy
  wait_for "PostgreSQL" "docker exec salesanalyzer-postgres pg_isready -U postgres" 30

  # Wait for Temporal gRPC to be reachable (first boot runs DB migrations, can take ~90s)
  wait_for "Temporal gRPC (port 7233)" "lsof -i :7233" 120

  log "PostgreSQL:  localhost:5432"
  log "Temporal:    localhost:7233"
  log "Temporal UI: http://localhost:8088"
}

start_minikube() {
  log "── Minikube ──"
  local mk_status
  mk_status=$(minikube status -f '{{.Host}}' 2>/dev/null || echo "Stopped")

  if [[ "$mk_status" == "Running" ]]; then
    log "Minikube already running"
  else
    log "Starting minikube..."
    minikube start --driver=docker --memory=4096 --cpus=2
    log "Minikube started"
  fi

  # Ensure kubectl context is set to minikube
  local current_ctx
  current_ctx=$(kubectl config current-context 2>/dev/null || echo "none")
  if [[ "$current_ctx" != "$KUBECTL_CONTEXT" ]]; then
    log "Switching kubectl context to minikube (was: $current_ctx)"
    kubectl config use-context "$KUBECTL_CONTEXT"
  fi
}

setup_argo() {
  log "── Argo Workflows ──"

  # Check if argo namespace exists
  if ! kctl get namespace "$ARGO_NAMESPACE" >/dev/null 2>&1; then
    log "Creating argo namespace..."
    kctl create namespace "$ARGO_NAMESPACE"
  else
    log "Argo namespace exists"
  fi

  # Check if Argo is installed (workflow-controller deployment)
  if ! kctl get deployment workflow-controller -n "$ARGO_NAMESPACE" >/dev/null 2>&1; then
    log "Installing Argo Workflows ${ARGO_VERSION}..."
    kctl apply -n "$ARGO_NAMESPACE" -f "$ARGO_INSTALL_URL"
    log "Waiting for Argo controller to be ready..."
    kctl rollout status deployment/workflow-controller -n "$ARGO_NAMESPACE" --timeout=120s
    kctl rollout status deployment/argo-server -n "$ARGO_NAMESPACE" --timeout=120s
  else
    log "Argo Workflows already installed"
    # Make sure deployments are available
    local wc_ready
    wc_ready=$(kctl get deployment workflow-controller -n "$ARGO_NAMESPACE" -o jsonpath='{.status.availableReplicas}' 2>/dev/null)
    if [[ "${wc_ready:-0}" -lt 1 ]]; then
      log "Argo controller not ready, waiting..."
      kctl rollout status deployment/workflow-controller -n "$ARGO_NAMESPACE" --timeout=120s
    fi
  fi

  # Create service account if needed
  if ! kctl get sa argo-workflow-sa -n "$ARGO_NAMESPACE" >/dev/null 2>&1; then
    log "Creating argo-workflow-sa service account..."
    kctl create sa argo-workflow-sa -n "$ARGO_NAMESPACE"
  fi

  # Create cluster role binding if needed
  if ! kctl get clusterrolebinding argo-workflow-sa-binding >/dev/null 2>&1; then
    log "Creating cluster role binding for argo-workflow-sa..."
    kctl create clusterrolebinding argo-workflow-sa-binding \
      --clusterrole=argo-cluster-role \
      --serviceaccount="${ARGO_NAMESPACE}:argo-workflow-sa"
  fi

  # Deploy workflow templates
  log "Deploying Argo workflow templates..."
  local templates_dir="$ROOT/forecaster/argo-workflows"
  if [[ -d "$templates_dir" ]]; then
    for tmpl in "$templates_dir"/*.yaml; do
      log "  Applying $(basename "$tmpl")"
      kctl apply -n "$ARGO_NAMESPACE" -f "$tmpl"
    done
  else
    warn "No workflow templates found at $templates_dir"
  fi

  # Port-forward argo-server if not already running
  if ! lsof -i :2746 >/dev/null 2>&1; then
    log "Starting Argo port-forward (localhost:2746)..."
    kctl port-forward svc/argo-server -n "$ARGO_NAMESPACE" 2746:2746 --address=0.0.0.0 > /tmp/sa-argo-pf.log 2>&1 &
    sleep 2
    if lsof -i :2746 >/dev/null 2>&1; then
      log "Argo port-forward active on :2746"
    else
      warn "Argo port-forward may not have started — check /tmp/sa-argo-pf.log"
    fi
  else
    log "Argo port-forward already active on :2746"
  fi
}

start_infra() {
  log "=========================================="
  log "  Starting Infrastructure"
  log "=========================================="
  start_compose_infra
  start_minikube
  setup_argo
  log ""
  log "Infrastructure is ready."
}

# =============================================================================
# SERVICES (Java + UI)
# =============================================================================

stop_service() {
  local svc=$1
  local port; port=$(svc_port "$svc")
  local pids; pids=$(lsof -ti:"$port" 2>/dev/null || true)
  if [[ -n "$pids" ]]; then
    log "Stopping $svc (port $port)"
    echo "$pids" | xargs kill -9 2>/dev/null || true
    sleep 1
  else
    log "$svc not running (port $port free)"
  fi
}

build_java() {
  local svc=$1
  local dir; dir=$(svc_dir "$svc")
  log "Building $svc..."
  mvn -f "$dir/pom.xml" clean package -DskipTests -q \
    && log "$svc build OK" \
    || die "$svc build FAILED"
}

start_service() {
  local svc=$1
  local dir; dir=$(svc_dir "$svc")
  local logfile; logfile=$(svc_log "$svc")
  case $svc in
    gateway|orchestrator|forecaster)
      build_java "$svc"
      local jar; jar=$(ls "$dir"/target/*.jar 2>/dev/null | grep -v sources | head -1)
      [[ -f "$jar" ]] || die "No jar found for $svc in $dir/target/"
      log "Starting $svc -> $logfile"
      java -jar "$jar" > "$logfile" 2>&1 &
      log "$svc started (PID $!)"
      ;;
    ui)
      log "Installing UI dependencies..."
      (cd "$dir" && npm install --silent)
      log "Starting UI (vite) -> $logfile"
      (cd "$dir" && ./node_modules/.bin/vite > "$logfile" 2>&1 &)
      log "UI started, log: $logfile"
      ;;
  esac
}

wait_healthy() {
  local svc=$1
  [[ "$svc" == "ui" ]] && return
  local port; port=$(svc_port "$svc")
  local logfile; logfile=$(svc_log "$svc")
  log "Waiting for $svc on :$port ..."
  local i=0
  until curl -sf "http://localhost:$port/actuator/health" -o /dev/null 2>/dev/null; do
    sleep 2; (( i += 2 ))
    if (( i >= 90 )); then
      warn "$svc not healthy after 90s — check $logfile"
      return
    fi
  done
  log "$svc is UP"
}

clean_service() {
  local svc=$1
  local dir; dir=$(svc_dir "$svc")
  log "mvn clean $svc..."
  mvn -f "$dir/pom.xml" clean -q && log "$svc cleaned" || die "$svc clean FAILED"
}

# =============================================================================
# STATUS
# =============================================================================

show_status() {
  echo ""
  log "=========================================="
  log "  SalesAnalyzer Status"
  log "=========================================="

  # Docker compose services
  echo ""
  echo "  Infrastructure (docker compose):"
  local compose_services=""
  compose_services=$(docker compose -f "$COMPOSE_FILE" ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null)
  if [[ -n "$compose_services" ]]; then
    echo "$compose_services" | sed 's/^/    /'
  else
    echo "    (not running)"
  fi

  # Minikube
  echo ""
  local mk_state
  mk_state=$(minikube status -f '{{.Host}}' 2>/dev/null || echo "Stopped")
  printf "  %-20s %s\n" "Minikube:" "$mk_state"

  # kubectl context
  local ctx
  ctx=$(kubectl config current-context 2>/dev/null || echo "none")
  printf "  %-20s %s\n" "kubectl context:" "$ctx"

  # Argo
  if [[ "$mk_state" == "Running" ]]; then
    local argo_wc
    argo_wc=$(kctl get deployment workflow-controller -n "$ARGO_NAMESPACE" -o jsonpath='{.status.availableReplicas}' 2>/dev/null || echo "0")
    printf "  %-20s %s replica(s)\n" "Argo controller:" "${argo_wc:-0}"
    local pf_pid
    pf_pid=$(lsof -ti :2746 2>/dev/null | head -1 || echo "")
    if [[ -n "$pf_pid" ]]; then
      printf "  %-20s active (PID %s)\n" "Argo port-forward:" "$pf_pid"
    else
      printf "  %-20s inactive\n" "Argo port-forward:"
    fi
  else
    printf "  %-20s minikube not running\n" "Argo:"
  fi

  # Services
  echo ""
  echo "  Application services:"
  local svc_p="" svc_pid=""
  for svc in "${ALL_SERVICES[@]}"; do
    svc_p="$(svc_port "$svc")"
    svc_pid="$(lsof -ti:"$svc_p" 2>/dev/null | head -1)"
    if [[ -n "$svc_pid" ]]; then
      printf "    %-18s running on :%s (PID %s)\n" "$svc:" "$svc_p" "$svc_pid"
    else
      printf "    %-18s stopped\n" "$svc:"
    fi
  done

  echo ""
  echo "  URLs:"
  echo "    Gateway Swagger  http://localhost:8080/swagger-ui.html"
  echo "    Temporal UI      http://localhost:8088"
  echo "    Argo UI          https://localhost:2746"
  echo "    App UI           http://localhost:5173"
  echo ""
}

stop_infra() {
  log "Stopping infrastructure..."

  # Stop argo port-forward
  local pf_pids; pf_pids=$(lsof -ti :2746 2>/dev/null || true)
  if [[ -n "$pf_pids" ]]; then
    log "Stopping Argo port-forward"
    echo "$pf_pids" | xargs kill 2>/dev/null || true
  fi

  # Stop docker compose services (postgres, temporal, temporal-ui)
  log "Stopping docker compose services..."
  docker compose -f "$COMPOSE_FILE" down

  # Stop minikube
  local mk_state
  mk_state=$(minikube status -f '{{.Host}}' 2>/dev/null || echo "Stopped")
  if [[ "$mk_state" == "Running" ]]; then
    log "Stopping minikube..."
    minikube stop
    log "Minikube stopped"
  else
    log "Minikube already stopped"
  fi

  log "Infrastructure stopped"
}

# =============================================================================
# ARGUMENT PARSING
# =============================================================================

STOP_ONLY=false
CLEAN_ONLY=false
STATUS_ONLY=false
INFRA_ONLY=false
SERVICES_ONLY=false
STOP_ALL=false
SERVICES=()

if [[ $# -eq 0 ]]; then
  # No args: start everything
  SERVICES=("${ALL_SERVICES[@]}")
elif [[ "$1" == "status" ]]; then
  STATUS_ONLY=true
elif [[ "$1" == "infra" ]]; then
  INFRA_ONLY=true
elif [[ "$1" == "services" ]]; then
  SERVICES_ONLY=true
  SERVICES=("${ALL_SERVICES[@]}")
elif [[ "$1" == "stop" ]]; then
  STOP_ONLY=true
  shift
  if [[ "${1:-}" == "all" ]]; then
    STOP_ALL=true
    SERVICES=("${ALL_SERVICES[@]}")
    shift
  else
    SERVICES=(${@:-${ALL_SERVICES[@]}})
  fi
elif [[ "$1" == "clean" ]]; then
  CLEAN_ONLY=true
  shift
  SERVICES=(${@:-${JAVA_SERVICES[@]}})
else
  # Named services only (skip infra)
  SERVICES_ONLY=true
  SERVICES=("$@")
fi

# Validate service names
if [[ ${#SERVICES[@]} -gt 0 ]]; then
  for svc in "${SERVICES[@]}"; do
    is_valid_service "$svc" || die "Unknown service '$svc'. Valid: ${ALL_SERVICES[*]}"
  done
fi

# ── Status ────────────────────────────────────────────────────────────────────
if $STATUS_ONLY; then
  show_status
  exit 0
fi

# ── Clean ─────────────────────────────────────────────────────────────────────
if $CLEAN_ONLY; then
  log "=== Cleaning: ${SERVICES[*]} ==="
  for svc in "${SERVICES[@]}"; do
    [[ "$svc" == "ui" ]] && { log "Skipping ui (not a Maven project)"; continue; }
    clean_service "$svc"
  done
  log "Done (clean only)"
  exit 0
fi

# ── Stop ──────────────────────────────────────────────────────────────────────
if $STOP_ONLY; then
  log "=== Stopping: ${SERVICES[*]} ==="
  for svc in "${SERVICES[@]}"; do stop_service "$svc"; done
  if $STOP_ALL; then
    stop_infra
  fi
  log "Done (stop)"
  exit 0
fi

# ── Infra only ────────────────────────────────────────────────────────────────
if $INFRA_ONLY; then
  start_infra
  exit 0
fi

# ── Full start or services-only ───────────────────────────────────────────────
if ! $SERVICES_ONLY; then
  start_infra
  echo ""
fi

log "=========================================="
log "  Starting Services: ${SERVICES[*]}"
log "=========================================="

# Stop existing
for svc in "${SERVICES[@]}"; do stop_service "$svc"; done

# Start
for svc in "${SERVICES[@]}"; do start_service "$svc"; done

# Wait for health
log "=== Checking health... ==="
for svc in "${SERVICES[@]}"; do wait_healthy "$svc"; done

log "=========================================="
log "  All done!"
log "=========================================="
echo ""
echo "  Swagger UIs:"
for svc in "${SERVICES[@]}"; do
  [[ "$svc" == "ui" ]] && continue
  echo "    $svc  http://localhost:$(svc_port "$svc")/swagger-ui.html"
done
echo "  UI           http://localhost:5173"
echo "  Temporal UI  http://localhost:8088"
echo "  Argo UI      https://localhost:2746"
echo ""
