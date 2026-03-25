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
error()   { echo -e "${RED}${BOLD}[ERR ]${RESET}  $*" >&2; }
die()     { error "$*"; exit 1; }

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

  # Prefer the system 'mvn' binary when available — it avoids the Maven
  # wrapper distribution download (which is silent with --no-transfer-progress
  # and looks like a hang on first run) and works around a bug in the mvnw
  # script where 'local distributionUrl' in parse_maven_config() is not
  # visible in maybe_download_maven(), causing "parameter not set" on
  # systems where the wrapper distribution is not yet cached.
  MVN_CMD="./mvnw"
  if command -v mvn &>/dev/null; then
    MVN_CMD="mvn"
    info "Using system Maven: $(mvn --version 2>&1 | head -1)"
  else
    info "No system mvn found — using Maven wrapper (may download ~10 MB on first run)"
  fi

  $MVN_CMD clean package -DskipTests --no-transfer-progress \
    || die "Maven build failed. Run 'mvn clean package -DskipTests' in /backend for details."
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

# ── Pre-flight port check ────────────────────────────────────
# Containers from previous runs under a different Compose project name
# (e.g. "docker" when compose was invoked directly from infrastructure/docker/)
# are NOT treated as orphans by --remove-orphans and will block port binds.
# Detect and stop any non-supporthub Docker container holding a required port.
_free_port() {
  local port="$1" label="$2"
  local culprit
  culprit="$(docker ps --format '{{.Names}}\t{{.Ports}}' \
    | awk -v p=":${port}->" '$0 ~ p {print $1}' \
    | grep -v '^supporthub-' || true)"
  if [[ -n "$culprit" ]]; then
    warn "Port ${port} (${label}) is held by container '${culprit}' — stopping it..."
    docker stop "$culprit" >/dev/null 2>&1 || true
  fi
}
_free_port "${REDIS_PORT:-6379}"           "Redis"
_free_port "${POSTGRES_PORT:-5432}"        "PostgreSQL"
_free_port "${MONGO_PORT:-27017}"          "MongoDB"
_free_port "2181"                          "Zookeeper"
_free_port "${KAFKA_PORT:-9092}"           "Kafka"
_free_port "${ELASTICSEARCH_PORT:-9200}"   "Elasticsearch"
_free_port "${MINIO_API_PORT:-9000}"       "MinIO API"
_free_port "${MINIO_CONSOLE_PORT:-9001}"   "MinIO Console"
_free_port "${STRAPI_PORT:-1337}"          "Strapi"
_free_port "${STRAPI_POSTGRES_PORT:-5433}" "Strapi PostgreSQL"

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
  info "Infrastructure access points:"
  echo "  PostgreSQL   → localhost:${POSTGRES_PORT:-5432}"
  echo "  MongoDB      → localhost:${MONGO_PORT:-27017}"
  echo "  Redis        → localhost:${REDIS_PORT:-6379}"
  echo "  Kafka        → localhost:${KAFKA_PORT:-9092}"
  echo "  Elasticsearch→ http://localhost:${ELASTICSEARCH_PORT:-9200}"
  echo "  MinIO API    → http://localhost:${MINIO_API_PORT:-9000}"
  echo "  MinIO Console→ http://localhost:${MINIO_CONSOLE_PORT:-9001}"
  echo "  Strapi CMS   → http://localhost:${STRAPI_PORT:-1337}"
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
echo "  ┌─────────────────────────────────────────────────────┐"
echo "  │  Customer Portal   →  http://localhost:3000         │"
echo "  │  Agent Dashboard   →  http://localhost:3001         │"
echo "  │  Admin Portal      →  http://localhost:3002         │"
echo "  └─────────────────────────────────────────────────────┘"
echo ""
echo -e "  ${BOLD}API & Tooling${RESET}"
echo "  ┌─────────────────────────────────────────────────────┐"
echo "  │  API Gateway       →  http://localhost:8080         │"
echo "  │  Swagger UI        →  http://localhost:8081/swagger-ui.html  │"
echo "  │  MinIO Console     →  http://localhost:9001         │"
echo "  │  Strapi CMS        →  http://localhost:1337/admin   │"
echo "  │  Elasticsearch     →  http://localhost:9200         │"
echo "  └─────────────────────────────────────────────────────┘"
echo ""
echo -e "  ${BOLD}Useful commands${RESET}"
echo "  ./run-local.sh --down          # stop everything"
echo "  ./run-local.sh --skip-build    # restart without rebuilding"
echo "  ./run-local.sh --infra-only    # infra only (for running services locally)"
echo "  docker logs -f supporthub-api-gateway"
echo "  docker logs -f supporthub-ticket-service"
echo ""
