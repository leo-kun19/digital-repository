# Run Local

## 1) Prepare local env files (one time)

```bash
cp config/dev.env.example config/dev.env
cp config/postgres.env.example config/postgres.env
```

Fill required placeholders in both files:

- `AAD_TENANT_ID`
- `AAD_CLIENT_ID`
- `AAD_CLIENT_SECRET`
- `AAD_REDIRECT_URI` (default already set for Vite)
- `APP_UI_BASE_URL` (`http://localhost:5173` for Vite dev)
- `ADMIN_EMAILS` (comma-separated allowlist for admin SSO role)

Fill Postgres settings in `config/postgres.env`:

- `DB_URL`
- `DB_USER`
- `DB_PASS`
- Recommended local default:
  - `DB_URL=jdbc:postgresql://127.0.0.1:5432/thesis_repo`

## 2) Run backend

### Recommended: PostgreSQL (Homebrew)

Start PostgreSQL:

```bash
brew services start postgresql@16
```

If your Homebrew formula name differs, use:

```bash
brew services start postgresql
```

Check status:

```bash
./scripts/postgres-status.sh
```

If local DB/users are broken or stale, reset to known dev defaults:

```bash
./scripts/reset-postgres.sh
./scripts/psql-check.sh
```

Run backend with PostgreSQL:

```bash
./run-postgres.sh
```

After filling `config/*.env` once, do not manually export `AAD_*` in terminal. Scripts load these values automatically.
When `APP_UI_BASE_URL` is set, backend `/login` and `/register` routes redirect to frontend routes so Spring's default form login page is not used in SSO mode.

### Alternative runner: `dev` profile (still PostgreSQL)

```bash
./run-dev.sh
```

This script loads `config/dev.env` and runs:

- Spring profiles: `aad,dev`
- DB: PostgreSQL datasource from `application.yml` and `DB_*` env vars/defaults

### PostgreSQL mode

This mode loads `config/postgres.env` and runs default Spring profile with Postgres datasource plus strict connectivity preflight.

## 3) Run frontend

```bash
cd admin-ui
npm install
npm run dev
```

Vite is served at `http://localhost:5173` and proxies backend paths to `http://localhost:8080`.
This includes `/oauth2/**` and `/login/oauth2/**` so the OAuth start + callback stay on `localhost:5173` while Spring completes authentication on the backend.

## Notes

- `config/*.env` is ignored by git and must never be committed.
- only `config/*.env.example` should contain committed placeholders.
- `data/` local runtime files are ignored by git.
- If scripts fail with `__FILL_ME__`, complete the missing env values first.
- Schema changes must be introduced via **new Flyway migrations**. Do not edit old applied migrations.
