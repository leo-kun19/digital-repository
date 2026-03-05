# Deployment Guide

## Required environment variables

Set these for backend runtime:

- `APP_AUTH_MODE` (`SSO`, `HYBRID`, or `LOCAL`)
- `ADMIN_EMAILS` (comma-separated staff emails that should become `ADMIN`)
- `AAD_TENANT_ID`
- `AAD_CLIENT_ID`
- `AAD_CLIENT_SECRET`
- `AAD_REDIRECT_URI`
- `DB_URL`
- `DB_USER`
- `DB_PASS`
- `FILE_STORAGE_ROOT` (maps to `file.storage-root`, default `./storage`)

## PostgreSQL setup (no Docker)

Example:

```sql
CREATE DATABASE thesis_repo;
CREATE USER thesis_user WITH PASSWORD '__FILL_ME__';
GRANT ALL PRIVILEGES ON DATABASE thesis_repo TO thesis_user;
```

App migration is handled by Flyway on startup.

## Backend run

```bash
./mvnw spring-boot:run
```

## Nginx reverse proxy

Reference file: `nginx/nginx.conf`

Current setup:

- serves frontend build from `root /var/www/thesis-admin`
- proxies `/api/` and `/actuator/` to `127.0.0.1:8080`
- upload limit: `client_max_body_size 5M`

Keep this upload limit aligned with Spring:

- `spring.servlet.multipart.max-file-size=5MB`
- `spring.servlet.multipart.max-request-size=5MB`
- `file.max-size-bytes=5242880`

## Microsoft Entra redirect URI

Configure the app registration with matching redirect URI:

- Local Vite: `http://localhost:5173/login/oauth2/code/azure`
- Production: `https://<your-domain>/login/oauth2/code/azure`

If redirect URI mismatches, OAuth login fails before reaching app onboarding.

## Packaging note (macOS)

When creating deployment ZIP archives on macOS, exclude Finder metadata folders:

- do not include `__MACOSX/`
- do not include `.DS_Store` files

Example:

```bash
zip -r deploy.zip . -x "__MACOSX/*" -x "*.DS_Store"
```
