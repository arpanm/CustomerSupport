#!/usr/bin/env bash
# ============================================================
# SupportHub — Local End-to-End Runner
# Usage: ./run-local.sh [--skip-build] [--infra-only] [--down]
#
# Options:
#   --skip-build   Skip Maven + npm builds (use existing JARs/dist)
#   --infra-only   Start only Postgres/Mongo/Redis/Kafka/ES/MinIO/Strapi
#   --down         Stop and remove all SupportHub containers
# ============================================================
set -euo pipefail

# ── Colours ────────────────────────────────────────────────
BOLD="\033[1m"; RESET="\033[0m"
RED="\033[31m"; GREEN="\033[32m"; YELLOW="\033[33m"; CYAN="\033[36m"

info()    { echo -e "${CYAN}${BOLD}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}${BOLD}[ OK ]${RESET}  $*"; }
warn()    { echo -e "${YELLOW}${BOLD}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}${BOLD}[ERR ]${RESET}  $*"; }
die() {
  echo ""
  echo -e "${RED}${BOLD}"
  echo "  ╔═══════════════════════════════════════════════════════╗"
  echo "  ║                    FATAL ERROR                        ║"
  echo "  ╚═══════════════════════════════════════════════════════╝"
  echo -e "${RESET}"
  error "$*"
  echo ""
  exit 1
}

# ── Paths ───────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_COMPOSE="${SCRIPT_DIR}/infrastructure/docker/docker-compose.yml"
SERVICES_COMPOSE="${SCRIPT_DIR}/infrastructure/docker/docker-compose.services.yml"
ENV_FILE="${SCRIPT_DIR}/infrastructure/docker/.env"
ENV_EXAMPLE="${SCRIPT_DIR}/infrastructure/docker/.env.example"
BACKEND_DIR="${SCRIPT_DIR}/backend"
FRONTEND_DIR="${SCRIPT_DIR}/frontend"

# ── Flags ───────────────────────────────────────────────────
SKIP_BUILD=false
INFRA_ONLY=false
DO_DOWN=false

for arg in "$@"; do
  case $arg in
    --skip-build) SKIP_BUILD=true ;;
    --infra-only) INFRA_ONLY=true ;;
    --down)       DO_DOWN=true ;;
    --help|-h)
      sed -n '2,9p' "$0" | sed 's/^# //'
      exit 0 ;;
    *) die "Unknown option: $arg  (run with --help)" ;;
  esac
done

# ── Tear-down ───────────────────────────────────────────────
if $DO_DOWN; then
  info "Stopping all SupportHub containers..."
  docker compose -f "$SERVICES_COMPOSE" \
    --env-file "$ENV_FILE" down --remove-orphans 2>/dev/null || true
  docker compose -f "$INFRA_COMPOSE" \
    --env-file "$ENV_FILE" down --remove-orphans 2>/dev/null || true
  success "All containers stopped."
  exit 0
fi

# ── Banner ──────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${CYAN}"
echo "  ╔═══════════════════════════════════════════╗"
echo "  ║        SupportHub — Local Stack           ║"
echo "  ╚═══════════════════════════════════════════╝"
echo -e "${RESET}"

# ── Prerequisites ───────────────────────────────────────────
info "Checking prerequisites..."

check_cmd() {
  command -v "$1" &>/dev/null || die "Required tool not found: $1  — please install it."
}

check_cmd docker
docker compose version &>/dev/null || die "Docker Compose v2 is required (got legacy 'docker-compose'). Update Docker Desktop or install the compose plugin."

if ! $INFRA_ONLY && ! $SKIP_BUILD; then
  check_cmd java
  JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
  [[ "$JAVA_VER" -ge 21 ]] 2>/dev/null || die "Java 21+ is required (found: Java ${JAVA_VER:-unknown})"

  check_cmd node
  NODE_VER=$(node --version | sed 's/v//' | cut -d. -f1)
  [[ "$NODE_VER" -ge 18 ]] || die "Node 18+ is required (found: Node ${NODE_VER})"
fi

success "Prerequisites OK"

# ── .env setup ──────────────────────────────────────────────
if [[ ! -f "$ENV_FILE" ]]; then
  info "No .env found — copying from .env.example"
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  warn "Copied .env.example → infrastructure/docker/.env"
  warn "Edit the .env file to set your ANTHROPIC_API_KEY before AI features will work."
fi

# Load env so we can read ANTHROPIC_API_KEY for the warning
set -o allexport
# shellcheck source=/dev/null
source "$ENV_FILE"
set +o allexport

if [[ -z "${ANTHROPIC_API_KEY:-}" ]] || [[ "$ANTHROPIC_API_KEY" == *"REPLACE"* ]]; then
  warn "ANTHROPIC_API_KEY is not set in infrastructure/docker/.env"
  warn "AI sentiment + resolution features will be disabled until you add it."
fi

# ── Port helpers ─────────────────────────────────────────────
# Tracks ports already claimed in this run to prevent two services
# from being assigned the same port when both defaults are busy.
_CLAIMED_PORTS=()

_port_is_claimed() {
  local p="$1"
  local q
  for q in "${_CLAIMED_PORTS[@]+"${_CLAIMED_PORTS[@]}"}"; do
    [[ "$q" == "$p" ]] && return 0
  done
  return 1
}

# Safely update KEY=VALUE in the .env file (handles URLs, special chars)
update_env() {
  local key="$1" val="$2" tmp="${ENV_FILE}.tmp"
  awk -v k="$key" -v v="$val" '
    substr($0,1,length(k)+1)==k"=" { print k"="v; next } { print }
  ' "$ENV_FILE" > "$tmp" && mv "$tmp" "$ENV_FILE"
}

# Echo the first port >= $1 that is free on the host AND not already claimed
# in this script run (avoids two services racing to the same free port).
find_free_port() {
  local port=$1
  while _port_is_claimed "$port" || \
        nc -z -w1 localhost "$port" 2>/dev/null || \
        docker ps --format '{{.Ports}}' 2>/dev/null | grep -q ":${port}->"; do
    port=$((port + 1))
  done
  echo "$port"
}

# claim_port VAR DEFAULT LABEL
# 1. Stops any alien (non-supporthub) Docker container holding the port.
# 2. If still busy or already claimed this run, finds the next free port,
#    writes it to .env, and exports it.
claim_port() {
  local var="$1" default="$2" label="$3"
  local port="${!var:-$default}"

  # Stop a foreign Docker container holding the port, if any
  local culprit
  culprit="$(docker ps --format '{{.Names}}\t{{.Ports}}' 2>/dev/null \
    | awk -v p=":${port}->" '$0 ~ p {print $1}' \
    | grep -v '^supporthub-' | head -1 || true)"
  if [[ -n "$culprit" ]]; then
    warn "Port ${port} (${label}) held by container '${culprit}' — stopping it"
    docker stop "$culprit" >/dev/null 2>&1 || true
    sleep 0.5
  fi

  # If still occupied OR already assigned to another service this run,
  # find the next genuinely free, unclaimed port.
  if _port_is_claimed "$port" || \
     nc -z -w1 localhost "$port" 2>/dev/null || \
     docker ps --format '{{.Ports}}' 2>/dev/null | grep -q ":${port}->"; then
    local new_port
    new_port="$(find_free_port $((port + 1)))"
    warn "Port ${port} (${label}) busy/claimed → reassigned to ${new_port}"
    update_env "$var" "$new_port"
    port="$new_port"
  fi

  _CLAIMED_PORTS+=("$port")
  export "${var}=${port}"
}

# ── Port claiming ────────────────────────────────────────────
info "Checking and claiming ports..."
claim_port "POSTGRES_PORT"        "${POSTGRES_PORT:-5432}"   "PostgreSQL"
claim_port "MONGO_PORT"           "${MONGO_PORT:-27017}"     "MongoDB"
claim_port "REDIS_PORT"           "${REDIS_PORT:-6379}"      "Redis"
claim_port "ZOOKEEPER_PORT"       "${ZOOKEEPER_PORT:-2181}"  "Zookeeper"
claim_port "KAFKA_PORT"           "${KAFKA_PORT:-9092}"      "Kafka"
claim_port "ELASTICSEARCH_PORT"   "${ELASTICSEARCH_PORT:-9200}" "Elasticsearch"
claim_port "MINIO_API_PORT"       "${MINIO_API_PORT:-9000}"  "MinIO API"
claim_port "MINIO_CONSOLE_PORT"   "${MINIO_CONSOLE_PORT:-9001}" "MinIO Console"
claim_port "STRAPI_POSTGRES_PORT" "${STRAPI_POSTGRES_PORT:-5433}" "Strapi DB"
claim_port "STRAPI_PORT"          "${STRAPI_PORT:-1337}"     "Strapi"

# Update any URL variables that embed a host port, then reload .env
update_env "DB_URL" \
  "jdbc:postgresql://localhost:${POSTGRES_PORT}/${POSTGRES_DB:-supporthub}"
update_env "DB_TEST_URL" \
  "jdbc:postgresql://localhost:${POSTGRES_PORT}/${POSTGRES_DB:-supporthub}_test"
update_env "MONGODB_URI" \
  "mongodb://${MONGO_USER:-supporthub}:${MONGO_PASSWORD:-supporthub_dev_password}@localhost:${MONGO_PORT}/${MONGO_DB:-supporthub}?authSource=admin"
update_env "KAFKA_SERVERS"     "localhost:${KAFKA_PORT}"
update_env "ELASTICSEARCH_URI" "http://localhost:${ELASTICSEARCH_PORT}"
update_env "AWS_S3_ENDPOINT"   "http://localhost:${MINIO_API_PORT}"
# Reload so Docker Compose and the rest of this script see the updated values
set -o allexport; source "$ENV_FILE"; set +o allexport
success "Ports OK (any conflicts were resolved above)"

# ── Build backend ───────────────────────────────────────────
if ! $SKIP_BUILD && ! $INFRA_ONLY; then
  info "Building backend (all 11 services) — this takes ~3 min on first run..."
  cd "$BACKEND_DIR"

  # Resolve JAVA_HOME if not already set.
  # 'java -XshowSettings:property' is the most portable method — it works on
  # Linux (openjdk, alternatives), macOS (stub + /usr/libexec/java_home),
  # sdkman, jenv, and Homebrew installs without any symlink gymnastics.
  if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(java -XshowSettings:property -version 2>&1 \
      | awk -F' = ' '/java\.home/ {print $2; exit}')"
    # java.home may point to the jre/ subdirectory on some JDKs — go one level up
    if [[ -n "${JAVA_HOME:-}" ]] && [[ ! -x "${JAVA_HOME}/bin/javac" ]] \
        && [[ -x "${JAVA_HOME}/../bin/javac" ]]; then
      JAVA_HOME="$(cd "${JAVA_HOME}/.." && pwd)"
    fi
    [[ -n "${JAVA_HOME:-}" ]] && export JAVA_HOME \
      && info "Auto-detected JAVA_HOME=$JAVA_HOME"
  fi

  # Prefer system 'mvn' over './mvnw' — avoids the wrapper's silent
  # ~10 MB distribution download and a sh-compatibility bug in mvnw.
  MVN_CMD="./mvnw"
  if command -v mvn &>/dev/null; then
    MVN_CMD="mvn"
    MVN_VER="$(mvn --version 2>&1 | head -1 | sed 's/Apache Maven //')"
    info "Using system Maven ${MVN_VER}"
  else
    info "No system mvn — using wrapper (first run downloads ~10 MB, then starts)"
  fi

  MVN_LOG="${TMPDIR:-/tmp}/supporthub-mvn-build.log"
  info "Maven output streams below AND is saved to: ${MVN_LOG}"
  info "First run downloads ~200 MB of dependencies — a heartbeat prints every 30s."

  # Background heartbeat so the terminal doesn't look frozen during downloads.
  _mvn_heartbeat() {
    local t=0
    while true; do
      sleep 30; t=$((t+30))
      printf '\033[36m\033[1m[INFO]\033[0m  ... Maven still running — %ds elapsed\n' "$t"
    done
  }
  _mvn_heartbeat &
  _MVN_HB_PID=$!

  # Run Maven; tee stdout+stderr to log so nothing is ever lost.
  # set +e prevents set -euo pipefail from aborting before we can clean up.
  set +e
  $MVN_CMD clean package -DskipTests --no-transfer-progress 2>&1 | tee "$MVN_LOG"
  _MVN_EXIT="${PIPESTATUS[0]}"
  set -e

  kill "$_MVN_HB_PID" 2>/dev/null; wait "$_MVN_HB_PID" 2>/dev/null || true

  if [[ $_MVN_EXIT -ne 0 ]]; then
    echo ""
    echo -e "${RED}${BOLD}"
    echo "  ╔═══════════════════════════════════════════════════════╗"
    echo "  ║               MAVEN BUILD FAILED                     ║"
    echo "  ╚═══════════════════════════════════════════════════════╝"
    echo -e "${RESET}"
    echo -e "  ${BOLD}Last 40 lines of build log (${MVN_LOG}):${RESET}"
    echo "  ─────────────────────────────────────────────────────────"
    tail -40 "$MVN_LOG" | sed 's/^/  /'
    echo ""
    die "Maven exited ${_MVN_EXIT}. Fix the errors above, then re-run (or use --skip-build if JARs exist)."
  fi

  echo ""
  echo -e "${GREEN}${BOLD}"
  echo "  ╔═══════════════════════════════════════════════════════╗"
  echo "  ║            BACKEND BUILD SUCCESSFUL                   ║"
  echo "  ╚═══════════════════════════════════════════════════════╝"
  echo -e "${RESET}"
  cd "$SCRIPT_DIR"
  success "Backend build complete"
fi

# ── Build frontend ──────────────────────────────────────────
if ! $SKIP_BUILD && ! $INFRA_ONLY; then
  info "Building frontend (3 apps)..."
  cd "$FRONTEND_DIR"

  # Install deps if node_modules is absent or package-lock changed
  if [[ ! -d node_modules ]] || [[ package-lock.json -nt node_modules/.install-stamp ]]; then
    npm ci --silent
    touch node_modules/.install-stamp
  fi

  VITE_API_BASE_URL=http://localhost:8080 \
  VITE_WS_URL=ws://localhost:8080 \
  VITE_TENANT_ID=${VITE_TENANT_ID:-00000000-0000-0000-0000-000000000001} \
    npm run build --workspaces --if-present \
    || die "Frontend build failed. Run 'npm run build --workspaces' in /frontend for details."

  cd "$SCRIPT_DIR"
  success "Frontend build complete"
fi

# ── Start infrastructure ────────────────────────────────────
info "Starting infrastructure services (Postgres · Mongo · Redis · Kafka · ES · MinIO · Strapi)..."
docker compose \
  -f "$INFRA_COMPOSE" \
  --env-file "$ENV_FILE" \
  up -d --remove-orphans

# ── Wait for infrastructure ─────────────────────────────────
wait_healthy() {
  local container="$1"
  local label="$2"
  local max_wait="${3:-120}"
  local elapsed=0

  printf "  Waiting for %-30s" "$label..."
  while true; do
    status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "missing")
    case "$status" in
      healthy)
        echo -e " ${GREEN}ready${RESET}"
        return 0 ;;
      missing|"")
        echo -e " ${YELLOW}no healthcheck — assuming ready${RESET}"
        return 0 ;;
    esac
    if [[ $elapsed -ge $max_wait ]]; then
      echo -e " ${RED}timed out after ${max_wait}s${RESET}"
      warn "Container ${container} did not become healthy. Check: docker logs ${container}"
      return 1
    fi
    sleep 3
    elapsed=$((elapsed + 3))
    printf "."
  done
}

echo ""
info "Waiting for infrastructure to become healthy..."
wait_healthy supporthub-postgres      "PostgreSQL"        90
wait_healthy supporthub-mongo         "MongoDB"           90
wait_healthy supporthub-redis         "Redis"             60
wait_healthy supporthub-zookeeper     "Zookeeper"         60
wait_healthy supporthub-kafka         "Kafka"            120
wait_healthy supporthub-elasticsearch "Elasticsearch"    180
wait_healthy supporthub-minio         "MinIO"             60
echo ""
success "All infrastructure services are up"

if $INFRA_ONLY; then
  echo ""
  info "Infrastructure access points (actual ports — may differ if defaults were busy):"
  printf "  PostgreSQL    → localhost:%s\n"             "${POSTGRES_PORT:-5432}"
  printf "  MongoDB       → localhost:%s\n"             "${MONGO_PORT:-27017}"
  printf "  Redis         → localhost:%s\n"             "${REDIS_PORT:-6379}"
  printf "  Zookeeper     → localhost:%s\n"             "${ZOOKEEPER_PORT:-2181}"
  printf "  Kafka         → localhost:%s\n"             "${KAFKA_PORT:-9092}"
  printf "  Elasticsearch → http://localhost:%s\n"      "${ELASTICSEARCH_PORT:-9200}"
  printf "  MinIO API     → http://localhost:%s\n"      "${MINIO_API_PORT:-9000}"
  printf "  MinIO Console → http://localhost:%s\n"      "${MINIO_CONSOLE_PORT:-9001}"
  printf "  Strapi CMS    → http://localhost:%s/admin\n" "${STRAPI_PORT:-1337}"
  echo ""
  exit 0
fi

# ── Verify JAR artifacts exist ──────────────────────────────
info "Verifying build artifacts..."
SERVICES=(
  "api-gateway:supporthub-api-gateway"
  "auth-service:supporthub-auth-service"
  "ticket-service:supporthub-ticket-service"
  "customer-service:supporthub-customer-service"
  "ai-service:supporthub-ai-service"
  "notification-service:supporthub-notification-service"
  "faq-service:supporthub-faq-service"
  "reporting-service:supporthub-reporting-service"
  "tenant-service:supporthub-tenant-service"
  "order-sync-service:supporthub-order-sync-service"
  "mcp-server:supporthub-mcp-server"
)
MISSING_JARS=()
for entry in "${SERVICES[@]}"; do
  svc="${entry%%:*}"
  artifact="${entry##*:}"
  jar="${BACKEND_DIR}/${svc}/target/${artifact}-1.0.0-SNAPSHOT.jar"
  if [[ ! -f "$jar" ]]; then
    MISSING_JARS+=("$jar")
  fi
done
if [[ ${#MISSING_JARS[@]} -gt 0 ]]; then
  error "Missing JARs (run without --skip-build or fix the Maven build):"
  for j in "${MISSING_JARS[@]}"; do error "  $j"; done
  die "Aborting — backend JARs not found."
fi

MISSING_DIST=()
for app in customer-portal agent-dashboard admin-portal; do
  if [[ ! -d "${FRONTEND_DIR}/apps/${app}/dist" ]]; then
    MISSING_DIST+=("frontend/apps/${app}/dist")
  fi
done
if [[ ${#MISSING_DIST[@]} -gt 0 ]]; then
  error "Missing frontend dist folders (run without --skip-build):"
  for d in "${MISSING_DIST[@]}"; do error "  $d"; done
  die "Aborting — frontend not built."
fi

success "All artifacts found"

# ── Start microservices + frontend ──────────────────────────
info "Starting 11 microservices + 3 frontend apps..."
docker compose \
  -f "$SERVICES_COMPOSE" \
  --env-file "$ENV_FILE" \
  up -d --remove-orphans

# ── Wait for api-gateway ─────────────────────────────────────
echo ""
info "Waiting for services to start (Spring Boot cold start ~60-90s)..."
wait_healthy supporthub-auth-service        "auth-service"        180
wait_healthy supporthub-ticket-service      "ticket-service"      180
wait_healthy supporthub-customer-service    "customer-service"    180
wait_healthy supporthub-faq-service         "faq-service"         180
wait_healthy supporthub-tenant-service      "tenant-service"      180
wait_healthy supporthub-notification-service "notification-service" 180
wait_healthy supporthub-ai-service          "ai-service"          180
wait_healthy supporthub-reporting-service   "reporting-service"   180
wait_healthy supporthub-order-sync-service  "order-sync-service"  180
wait_healthy supporthub-mcp-server          "mcp-server"          180
wait_healthy supporthub-api-gateway         "api-gateway"         180

# ── Done ─────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}"
echo "  ╔═══════════════════════════════════════════════════════╗"
echo "  ║             SupportHub is running locally             ║"
echo "  ╚═══════════════════════════════════════════════════════╝"
echo -e "${RESET}"
echo -e "  ${BOLD}Frontend Apps${RESET}"
echo "  ┌──────────────────────────────────────────────────────────┐"
echo "  │  Customer Portal   →  http://localhost:3000              │"
echo "  │  Agent Dashboard   →  http://localhost:3001              │"
echo "  │  Admin Portal      →  http://localhost:3002              │"
echo "  └──────────────────────────────────────────────────────────┘"
echo ""
echo -e "  ${BOLD}API & Services${RESET}"
echo "  ┌──────────────────────────────────────────────────────────┐"
printf "  │  API Gateway       →  http://localhost:8080              │\n"
printf "  │  Swagger UI        →  http://localhost:8081/swagger-ui.html  │\n"
printf "  │  MinIO Console     →  http://localhost:%-5s               │\n" "${MINIO_CONSOLE_PORT:-9001}"
printf "  │  Strapi CMS        →  http://localhost:%-5s/admin         │\n" "${STRAPI_PORT:-1337}"
printf "  │  Elasticsearch     →  http://localhost:%-5s               │\n" "${ELASTICSEARCH_PORT:-9200}"
echo "  └──────────────────────────────────────────────────────────┘"
echo ""
echo -e "  ${BOLD}Infrastructure (actual ports — may differ if defaults were busy)${RESET}"
echo "  ┌──────────────────────────────────────────────────────────┐"
printf "  │  PostgreSQL        →  localhost:%-5s                    │\n" "${POSTGRES_PORT:-5432}"
printf "  │  MongoDB           →  localhost:%-5s                  │\n" "${MONGO_PORT:-27017}"
printf "  │  Redis             →  localhost:%-5s                    │\n" "${REDIS_PORT:-6379}"
printf "  │  Kafka             →  localhost:%-5s                    │\n" "${KAFKA_PORT:-9092}"
printf "  │  MinIO API         →  http://localhost:%-5s               │\n" "${MINIO_API_PORT:-9000}"
echo "  └──────────────────────────────────────────────────────────┘"
echo ""
echo -e "  ${BOLD}Useful commands${RESET}"
echo "  ./run-local.sh --down          # stop everything"
echo "  ./run-local.sh --skip-build    # restart without rebuilding"
echo "  ./run-local.sh --infra-only    # infra only (for running services locally)"
echo "  docker logs -f supporthub-api-gateway"
echo "  docker logs -f supporthub-ticket-service"
echo ""
