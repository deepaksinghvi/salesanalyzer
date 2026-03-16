#!/bin/zsh
# Usage:
#   ./dev.sh                        # stop + start all services
#   ./dev.sh gateway forecaster     # stop + start only named services
#   ./dev.sh stop                   # stop all services
#   ./dev.sh stop gateway ui        # stop only named services
#   ./dev.sh clean                  # mvn clean all Java services (gateway, orchestrator, forecaster)
#   ./dev.sh clean gateway          # mvn clean only the named Java service(s)

ROOT="$(cd "$(dirname "$0")" && pwd)"

# ── Java / Maven setup ─────────────────────────────────────────────────────────
# Source zshrc to pick up user PATH (Java, Maven, etc.)
[[ -f "$HOME/.zshrc" ]] && source "$HOME/.zshrc" 2>/dev/null 1>/dev/null || true
[[ -f "$HOME/.zprofile" ]] && source "$HOME/.zprofile" 2>/dev/null 1>/dev/null || true

# Auto-detect JAVA_HOME if not set
if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v /usr/libexec/java_home &>/dev/null; then
    export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
  else
    # Find java binary and derive JAVA_HOME from it
    JAVA_BIN="$(command -v java 2>/dev/null)"
    if [[ -n "$JAVA_BIN" ]]; then
      export JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    fi
  fi
fi
[[ -n "${JAVA_HOME:-}" ]] && export PATH="$JAVA_HOME/bin:$PATH"

# ── Service definitions ────────────────────────────────────────────────────────
ALL_SERVICES=(gateway orchestrator forecaster ui)

svc_port() {
  case $1 in
    gateway)      echo 8080 ;;
    orchestrator) echo 8081 ;;
    forecaster)   echo 8082 ;;
    ui)           echo 5173 ;;
  esac
}

svc_dir() {
  echo "$ROOT/$1"
}

svc_log() {
  echo "/tmp/sa-$1.log"
}

# ── Helpers ────────────────────────────────────────────────────────────────────
log() { echo "[$(date '+%H:%M:%S')] $*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

is_valid_service() {
  for s in "${ALL_SERVICES[@]}"; do [[ "$s" == "$1" ]] && return 0; done
  return 1
}

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
      local logfile; logfile=$(svc_log "$svc")
      log "Starting UI (npm run dev) -> $logfile"
      (cd "$dir" && npm run dev > "$logfile" 2>&1 &)
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
      log "WARNING: $svc not healthy after 90s — check $logfile"
      return
    fi
  done
  log "$svc is UP"
}

JAVA_SERVICES=(gateway orchestrator forecaster)

clean_service() {
  local svc=$1
  local dir; dir=$(svc_dir "$svc")
  log "mvn clean $svc..."
  mvn -f "$dir/pom.xml" clean -q && log "$svc cleaned" || die "$svc clean FAILED"
}

# ── Argument parsing ───────────────────────────────────────────────────────────
STOP_ONLY=false
CLEAN_ONLY=false
SERVICES=()

if [[ $# -eq 0 ]]; then
  SERVICES=("${ALL_SERVICES[@]}")
elif [[ "$1" == "stop" ]]; then
  STOP_ONLY=true
  shift
  SERVICES=(${@:-${ALL_SERVICES[@]}})
elif [[ "$1" == "clean" ]]; then
  CLEAN_ONLY=true
  shift
  SERVICES=(${@:-${JAVA_SERVICES[@]}})
else
  SERVICES=("$@")
fi

for svc in "${SERVICES[@]}"; do
  is_valid_service "$svc" || die "Unknown service '$svc'. Valid: ${ALL_SERVICES[*]}"
done

# ── Clean ──────────────────────────────────────────────────────────────────────
if $CLEAN_ONLY; then
  log "=== Cleaning: ${SERVICES[*]} ==="
  for svc in "${SERVICES[@]}"; do
    [[ "$svc" == "ui" ]] && { log "Skipping ui (not a Maven project)"; continue; }
    clean_service "$svc"
  done
  log "Done (clean only)"
  exit 0
fi

# ── Stop ───────────────────────────────────────────────────────────────────────
log "=== Stopping: ${SERVICES[*]} ==="
for svc in "${SERVICES[@]}"; do stop_service "$svc"; done

if $STOP_ONLY; then
  log "Done (stop only)"
  exit 0
fi

# ── Start ──────────────────────────────────────────────────────────────────────
log "=== Starting: ${SERVICES[*]} ==="
for svc in "${SERVICES[@]}"; do start_service "$svc"; done

# ── Wait for health ────────────────────────────────────────────────────────────
log "=== Checking health... ==="
for svc in "${SERVICES[@]}"; do wait_healthy "$svc"; done

log "=== All done ==="
echo ""
echo "  Swagger UIs:"
for svc in "${SERVICES[@]}"; do
  [[ "$svc" == "ui" ]] && continue
  echo "    $svc  http://localhost:$(svc_port "$svc")/swagger-ui.html"
done
echo "  UI    http://localhost:5173"
echo ""
