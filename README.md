# Digital Repository Thesis Master

Spring Boot backend + React/Vite frontend for thesis/publication workflow, repository search, and controlled publishing.

## Key Docs

- Local run guide: `docs/RUN_LOCAL.md`
- Deployment guide: `docs/DEPLOYMENT.md`
- Live demo script: `docs/DEMO_SCRIPT.md`
- Auth model (domain + admin allowlist): `docs/AUTH_MODEL.md`
- Finalization checklist: `docs/FINALIZATION_CHECKLIST.md`

## Quick Start

1. Copy env templates:

```bash
cp config/dev.env.example config/dev.env
cp config/postgres.env.example config/postgres.env
```

2. Fill placeholders in `config/*.env`:
   - `AAD_TENANT_ID`
   - `AAD_CLIENT_ID`
   - `AAD_CLIENT_SECRET`
   - `AAD_REDIRECT_URI`
   - `APP_UI_BASE_URL` (`http://localhost:5173` for Vite dev)
   - `ADMIN_EMAILS`
   - Postgres values for `config/postgres.env`

3. Run backend:

```bash
# PostgreSQL + AAD (dev profile)
./run-dev.sh

# PostgreSQL + AAD (preflight checks)
./run-postgres.sh
```

No manual `export AAD_*` is required after `config/*.env` is filled. The run scripts load env files automatically.

In SSO mode (`APP_AUTH_MODE=SSO` or `AAD`), local email/password auth UI is disabled; login/register routes redirect to the frontend UI when `APP_UI_BASE_URL` is configured.

4. Run frontend:

```bash
cd admin-ui
npm install
npm run dev
```

## Security and Local Files

- `config/*.env` is local-only and ignored by git.
- Template files (`config/*.env.example`) are committed with placeholders only.
- Upload limit is aligned to **5MB** across Spring and Nginx.

## Build Checks

```bash
./mvnw test
cd admin-ui && npm run build
```
