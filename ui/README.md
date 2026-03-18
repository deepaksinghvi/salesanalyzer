# UI

React single-page application for the SalesAnalyzer platform. Provides dashboards, data upload, and tenant/user management.

## Tech Stack

- React 18, TypeScript, Vite
- Tailwind CSS
- Recharts (charts)
- TanStack React Query (data fetching)
- React Router v6

## Port

- **5173** (Vite dev server)
- **3000** (production via nginx in Docker)

## Pages

| Route | Page | Access |
|-------|------|--------|
| `/login` | Login | Public |
| `/dashboard` | Sales dashboard with charts and forecasting | All roles |
| `/upload` | CSV file upload | Admin, SuperAdmin |
| `/tenants` | Tenant management | SuperAdmin |
| `/users` | User management | Admin, SuperAdmin |

## Dashboard Features

- Revenue trend chart (actual vs forecast by month)
- Category breakdown bar chart
- Period filter (month / quarter / year / all)
- Run Forecast button (XGBoost/Prophet, Admin+ only)
- Clear Forecast button (Admin+ only)
- Refresh button
- Top categories ranking

## Role-Based Visibility

| Feature | Viewer | Admin | SuperAdmin |
|---------|--------|-------|------------|
| View dashboard | Yes | Yes | Yes |
| Upload data | No | Yes | Yes |
| Run/clear forecast | No | Yes | Yes |
| Manage users | No | Yes | Yes |
| Manage tenants | No | No | Yes |

## Configuration

The API base URL is configured via Vite environment variable:

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Gateway API base URL |

## Running

```bash
npm install
npm run dev
```

## Building

```bash
npm run build
```

Output goes to `dist/`, served by nginx in Docker.
