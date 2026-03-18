# Docker

Docker Compose configurations for running SalesAnalyzer infrastructure and full stack.

## Files

| File | Purpose |
|------|---------|
| `docker-compose.infra.yml` | Infrastructure only: PostgreSQL + Temporal + Temporal UI |
| `docker-compose.yml` | Full stack: all services + infrastructure |

## Infrastructure Only (for local dev)

Used by `dev.sh` to start just the infrastructure while running services locally:

```bash
docker compose -f docker-compose.infra.yml up -d
```

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL 16 | 5432 | Database with named volume `postgres_data` |
| Temporal Server | 7233 | Workflow orchestration (auto-setup:1.25.2) |
| Temporal UI | 8088 | Web UI for Temporal workflows |

Data persists across restarts via the `postgres_data` Docker volume.

## Full Stack

Builds and runs everything including all Java services and the React UI:

```bash
docker compose up --build
```

| Service | Port |
|---------|------|
| PostgreSQL | 5432 |
| Temporal | 7233 |
| Temporal UI | 8088 |
| Gateway | 8080 |
| Orchestrator | 8081 |
| Forecaster | 8082 |
| UI (nginx) | 3000 |

## Stopping

```bash
docker compose -f docker-compose.infra.yml down      # infra only
docker compose down                                    # full stack
docker compose -f docker-compose.infra.yml down -v    # infra + delete data
```
