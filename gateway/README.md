# Gateway Service

The **Gateway** is the main API entry point for the SalesAnalyzer platform. It handles authentication, CSV uploads, tenant/user management, and proxies forecast requests through the Orchestrator.

## Tech Stack

- Java 21, Spring Boot 3.2, Spring Security (JWT)
- Spring Data JPA + Flyway (PostgreSQL)
- Lombok, springdoc-openapi (Swagger)

## Port

- **8080** (default)

## Endpoints

### Auth (public)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | Login, returns JWT token + tenant info |

### Insights (authenticated)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/insights/{tenantId}?period=` | Sales insights (period: month/quarter/year/all) |

### Upload (Admin, SuperAdmin)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/uploads` | Upload CSV file (multipart: file + periodType) |

### Forecast (Admin, SuperAdmin)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/forecast/trigger` | Trigger forecast via Orchestrator (Temporal) |
| POST | `/api/forecast/run-local` | Trigger local fallback forecast |
| DELETE | `/api/forecast/{tenantId}` | Clear forecast data |

### Tenants (SuperAdmin)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/tenants` | List all tenants |
| GET | `/api/tenants/{id}` | Get tenant by ID |
| POST | `/api/tenants` | Create tenant |
| PUT | `/api/tenants/{id}` | Update tenant |
| DELETE | `/api/tenants/{id}` | Delete tenant |

### Users (Admin, SuperAdmin)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users` | All users (SuperAdmin only) |
| GET | `/api/users/tenant/{tenantId}` | Users for a tenant |
| POST | `/api/users` | Create user |
| DELETE | `/api/users/{id}` | Delete user |
| PUT | `/api/users/{id}/password` | Reset password (SuperAdmin only) |

## Configuration

Key properties in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8080 | Service port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/salesanalyzer` | Database URL |
| `app.jwt.secret` | (configured) | JWT signing key |
| `app.jwt.expiration-ms` | 86400000 (24h) | Token expiration |
| `app.upload.tmp-dir` | `/tmp/salesanalyzer` | CSV upload staging directory |
| `app.orchestrator.base-url` | `http://localhost:8081` | Orchestrator service URL |
| `app.forecaster.base-url` | `http://localhost:8082` | Forecaster service URL |

## Database

Flyway runs on startup and applies `src/main/resources/db/migration/V1__init_schema.sql` which creates all tables, the materialized view, and seed data.

## Running

```bash
mvn spring-boot:run
# or
mvn package -DskipTests && java -jar target/gateway-1.0.0-SNAPSHOT.jar
```

Swagger UI: http://localhost:8080/swagger-ui.html
