# SupportHub — Observability Stack

Task: OBS-001

This directory contains configuration for the SupportHub observability stack: Prometheus, Grafana, Loki, Promtail, and Langfuse.

---

## Services

| Service    | Port  | Purpose                          |
|------------|-------|----------------------------------|
| Grafana    | 3010  | Dashboards and alerting          |
| Prometheus | 9090  | Metrics scraping and storage     |
| Loki       | 3100  | Log aggregation                  |
| Promtail   | 9080  | Log shipping agent               |
| Langfuse   | 3030  | LLM observability (AI tracing)   |

---

## Starting the Observability Stack

The observability stack is defined in a separate Compose file so it can be started independently of the main application stack.

```bash
# From the repository root or infrastructure/docker directory
docker compose -f infrastructure/docker/docker-compose-obs.yml up -d

# To stop
docker compose -f infrastructure/docker/docker-compose-obs.yml down

# To stop and remove volumes (WARNING: destroys all metrics and log data)
docker compose -f infrastructure/docker/docker-compose-obs.yml down -v
```

The main application stack must be started separately:

```bash
docker compose -f infrastructure/docker/docker-compose.yml up -d
```

---

## Accessing the UIs

### Grafana — http://localhost:3010

- Username: `admin`
- Password: `supporthub_grafana`

Two dashboards are provisioned automatically on startup:
- **SupportHub — Service Overview**: request rate, error rate, P99 latency, JVM heap, active threads, and service health table.
- **SupportHub — Ticket Metrics**: tickets created/resolved per hour, SLA breach count, open ticket count, avg resolution time.

### Prometheus — http://localhost:9090

No authentication. Use the Prometheus UI to explore raw metrics and verify scrape targets under **Status > Targets**.

All 11 SupportHub microservices are scraped via `/actuator/prometheus`:

| Service             | Target                        |
|---------------------|-------------------------------|
| api-gateway         | host.docker.internal:8080     |
| auth-service        | host.docker.internal:8081     |
| ticket-service      | host.docker.internal:8082     |
| customer-service    | host.docker.internal:8083     |
| ai-service          | host.docker.internal:8084     |
| notification-service| host.docker.internal:8085     |
| faq-service         | host.docker.internal:8086     |
| reporting-service   | host.docker.internal:8087     |
| tenant-service      | host.docker.internal:8088     |
| order-sync-service  | host.docker.internal:8089     |
| mcp-server          | host.docker.internal:8090     |

### Loki — http://localhost:3100

No authentication. Access directly or query through Grafana's Explore view (select the Loki datasource).

Promtail ships logs from all containers labelled `com.docker.compose.project=supporthub`. Logs are retained for 31 days.

### Langfuse — http://localhost:3030

LLM tracing for the `ai-service`. On first access, create an admin account through the web UI. Set `LANGFUSE_PUBLIC_KEY` and `LANGFUSE_SECRET_KEY` environment variables in `ai-service` using keys generated in the Langfuse project settings.

---

## Manual Dashboard Import (if provisioning fails)

If the dashboards are not visible after startup:

1. Open Grafana at http://localhost:3010 and log in.
2. Go to **Dashboards > Import**.
3. Click **Upload JSON file**.
4. Select one of the dashboard JSON files:
   - `infrastructure/observability/grafana/dashboards/supporthub-overview.json`
   - `infrastructure/observability/grafana/dashboards/ticket-metrics.json`
5. Choose the `Prometheus` datasource when prompted, then click **Import**.

---

## Directory Structure

```
infrastructure/
  docker/
    docker-compose-obs.yml      # Observability stack Compose file
  observability/
    prometheus/
      prometheus.yml            # Prometheus scrape configuration (all 11 services)
    grafana/
      provisioning/
        datasources/
          datasources.yml       # Auto-provision Prometheus + Loki datasources
        dashboards/
          dashboards.yml        # Auto-provision dashboard file provider
      dashboards/
        supporthub-overview.json   # Service overview dashboard
        ticket-metrics.json        # Business ticket metrics dashboard
    loki/
      loki-config.yml           # Loki single-node configuration
    promtail/
      promtail-config.yml       # Promtail Docker log scraping configuration
```

---

## Adding New Metrics to Dashboards

1. In the Grafana UI, open the dashboard and click **Edit** on the panel.
2. Modify the PromQL expression and save.
3. To persist changes back to the repository, export the dashboard JSON via **Dashboard settings > JSON Model**, copy the JSON, and replace the file in `infrastructure/observability/grafana/dashboards/`.

---

## Troubleshooting

**Prometheus shows targets as DOWN**
- Ensure the Spring Boot services are running and exposing `/actuator/prometheus`.
- On Linux, `host.docker.internal` may not resolve automatically. Add `--add-host=host.docker.internal:host-gateway` to the Prometheus container or use the host IP directly.

**No logs appear in Loki / Grafana Explore**
- Check Promtail logs: `docker logs supporthub-promtail`
- Verify Docker containers have the label `com.docker.compose.project=supporthub` (set in `docker-compose.yml`).
- Promtail needs read access to `/var/lib/docker/containers` and `/var/run/docker.sock`.

**Grafana dashboards not loading**
- Check provisioning logs: `docker logs supporthub-grafana | grep -i provision`
- Verify the dashboard JSON files are mounted correctly at `/var/lib/grafana/dashboards`.
