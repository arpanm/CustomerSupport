# SupportHub — Local Development Docker Compose Stack

Task: INFRA-001

## Quick Start

```bash
# 1. Copy environment file and review defaults
cp infrastructure/docker/.env.example infrastructure/docker/.env

# 2. Start the full stack (all services)
docker compose -f infrastructure/docker/docker-compose.yml up -d

# 3. Check all services are healthy
docker compose -f infrastructure/docker/docker-compose.yml ps

# 4. Tail logs for a specific service
docker compose -f infrastructure/docker/docker-compose.yml logs -f kafka

# 5. Stop everything (data volumes are preserved)
docker compose -f infrastructure/docker/docker-compose.yml down

# 6. Stop and wipe all data volumes (full reset)
docker compose -f infrastructure/docker/docker-compose.yml down -v
```

## Starting a Subset of Services

```bash
# Databases only (fastest startup for backend development)
docker compose -f infrastructure/docker/docker-compose.yml up -d postgres mongo redis

# Databases + Kafka (for event-driven service development)
docker compose -f infrastructure/docker/docker-compose.yml up -d postgres mongo redis zookeeper kafka

# Everything except Strapi (if cms/ is not yet initialised)
docker compose -f infrastructure/docker/docker-compose.yml up -d \
  postgres mongo redis zookeeper kafka elasticsearch minio
```

## Service Port Reference

| Service           | Container Name               | Host Port(s)       | Purpose                                 |
|-------------------|------------------------------|--------------------|-----------------------------------------|
| PostgreSQL 16     | supporthub-postgres          | 5432               | Main application database + pgvector    |
| MongoDB 7         | supporthub-mongo             | 27017              | Document store (tickets, activities)    |
| Redis 7           | supporthub-redis             | 6379               | Cache, session, idempotency store       |
| Zookeeper         | supporthub-zookeeper         | 2181               | Kafka coordinator (internal use)        |
| Kafka             | supporthub-kafka             | 9092               | Event streaming (all domain events)     |
| Elasticsearch 8   | supporthub-elasticsearch     | 9200, 9300         | Full-text search and FAQ indexing       |
| MinIO (API)       | supporthub-minio             | 9000               | S3-compatible object storage API        |
| MinIO (Console)   | supporthub-minio             | 9001               | MinIO web management UI                 |
| Strapi Postgres   | supporthub-strapi-postgres   | 5433               | Strapi's dedicated CMS database         |
| Strapi v5 CMS     | supporthub-strapi            | 1337               | FAQ / content management system         |

## Default Credentials

| Service       | Username        | Password                    | Notes                            |
|---------------|-----------------|-----------------------------|----------------------------------|
| PostgreSQL    | supporthub      | supporthub_dev_password     | Main DB                          |
| MongoDB       | supporthub      | supporthub_dev_password     | authSource=admin                 |
| Redis         | (none)          | supporthub_dev_password     | Use `-a` flag with redis-cli     |
| MinIO         | minioadmin      | minioadmin                  | Also used as AWS key/secret      |
| Strapi DB     | strapi          | strapi_dev_password         | Strapi's own Postgres            |
| Strapi Admin  | (set on first login) | (set on first login)  | http://localhost:1337/admin      |

## Health Check Commands

Verify each service is ready before starting backend services:

```bash
# PostgreSQL
docker exec supporthub-postgres pg_isready -U supporthub -d supporthub

# PostgreSQL — verify pgvector extension
docker exec supporthub-postgres psql -U supporthub -d supporthub \
  -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"

# MongoDB
docker exec supporthub-mongo mongosh --quiet \
  -u supporthub -p supporthub_dev_password --authenticationDatabase admin \
  --eval "db.adminCommand('ping')"

# Redis
docker exec supporthub-redis redis-cli -a supporthub_dev_password ping

# Kafka — list topics
docker exec supporthub-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

# Elasticsearch — cluster health
curl -s http://localhost:9200/_cluster/health | jq .status

# MinIO — liveness
curl -s http://localhost:9000/minio/health/live && echo "MinIO OK"

# Strapi
curl -s http://localhost:1337/_health
```

## Connection Strings for Backend Services

Copy these into your `application-dev.yml` or local `.env` for each service:

```yaml
# PostgreSQL
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/supporthub
    username: supporthub
    password: supporthub_dev_password

# MongoDB
  data:
    mongodb:
      uri: mongodb://supporthub:supporthub_dev_password@localhost:27017/supporthub?authSource=admin

# Redis
  data:
    redis:
      host: localhost
      port: 6379
      password: supporthub_dev_password

# Kafka
  kafka:
    bootstrap-servers: localhost:9092

# Elasticsearch
  elasticsearch:
    uris: http://localhost:9200
```

## Strapi Setup (First Time)

Strapi requires the `cms/` directory to be present (it is mounted as `/app` in the container):

```bash
# If cms/ directory doesn't exist yet, initialise a new Strapi project:
cd /path/to/repo
npx create-strapi-app@latest cms --dbclient=postgres \
  --dbhost=localhost --dbport=5433 --dbname=strapi \
  --dbusername=strapi --dbpassword=strapi_dev_password \
  --no-run

# Then restart the strapi container
docker compose -f infrastructure/docker/docker-compose.yml restart strapi
```

## MinIO Bucket Setup

After MinIO starts, create the dev bucket:

```bash
# Using MinIO Client (mc) — install from https://min.io/docs/minio/linux/reference/minio-mc.html
mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb local/supporthub-dev
mc anonymous set download local/supporthub-dev/public

# Or open the web console at http://localhost:9001
# Login: minioadmin / minioadmin
```

## Troubleshooting

### Elasticsearch fails to start (vm.max_map_count)
```bash
# On Linux host — increase virtual memory map limit
sudo sysctl -w vm.max_map_count=262144
# To persist across reboots, add to /etc/sysctl.conf:
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
```

### Kafka consumer cannot connect
Kafka advertises `localhost:9092` for host access. If running backend services
inside Docker, use `kafka:29092` (the internal listener) instead.

### Port already in use
Change the host-side port in `.env` (e.g., `POSTGRES_PORT=5434`) without
modifying `docker-compose.yml`. Remember to update your service connection
strings accordingly.

### pgvector extension missing
The init script in `init-db/01-init-pgvector.sql` runs only on the very first
container start (when the volume is empty). If you need to re-run it on an
existing volume:
```bash
docker exec supporthub-postgres psql -U supporthub -d supporthub \
  -c "CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"
```
