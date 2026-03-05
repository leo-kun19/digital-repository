# FINALIZATION CHECKLIST

## 1) Environment

- Backend runs with Microsoft SSO profile for student/lecturer sign-in:

```bash
cp config/postgres.env.example config/postgres.env
# fill AAD_* and DB_* in config/postgres.env once
./run-postgres.sh
```

- Frontend:

```bash
cd admin-ui
npm install
npm run dev
```

## 2) Build Verification

- Backend tests:

```bash
./mvnw test
```

- Frontend production build:

```bash
cd admin-ui
npm run build
```

## 3) Demo Admin Account (non-SSO)

Option A (seeded):

- Enable seed in `application.yml` or env:

```bash
export APP_SEED_ENABLED=true
```

- Seed creates:
  - `admin@example.com` / `Admin123!`

Option B (SQL insert, if seed is disabled):

```sql
INSERT INTO users (email, password_hash, role, auth_provider)
VALUES (
  'admin@example.com',
  '$2a$10$8gO1v9.Yz7Zk0k3SPQ7m6eYz4Dgexj6YXN6ni4eWh05rq6ArlTcKu',
  'ADMIN',
  'LOCAL'
)
ON CONFLICT (email) DO NOTHING;
```

> BCrypt hash above corresponds to password `Admin123!`.

## 4) Acceptance Script

### A. SSO signup + onboarding

1. Open `/register` and click **Sign up with Microsoft**.
2. Sign in with a university account:
   - `@my.sampoernauniversity.ac.id` => STUDENT
   - `@sampoernauniversity.ac.id` => LECTURER
3. Confirm redirect to `/onboarding` for first login.
4. Complete required fields and submit.
5. Confirm redirect to role dashboard.

### B. Onboarding guard

1. Login with an account that has incomplete profile.
2. Try opening `/student/dashboard` or `/lecturer/dashboard` directly.
3. Confirm automatic redirect to `/onboarding`.

### C. Master data dropdowns

1. On `/onboarding` and repository search page:
   - Faculty dropdown shows FET/FOB/FOE/FAS names.
   - Study Program list depends on selected faculty.

### D. Repository search filters

1. Open `/` repository search page.
2. Confirm there is no global query input.
3. Filter by faculty/program/year and verify results are filtered.

### E. Supervisor restriction

1. Login as student and open new registration page.
2. Confirm supervisor list only contains lecturers in same study program (and faculty when set).
3. Attempt manual API request with mismatched supervisor IDs.
4. Confirm API returns `400` with mismatch message.

### F. PDF-only upload

1. Open student submission upload page.
2. Try non-PDF file (`.docx`/`.txt`) => must fail.
3. Try malformed fake `.pdf` (wrong header) => must fail.
4. Upload valid PDF => succeeds.

### G. Download protection

1. Log out and open published item download URL.
2. Confirm access blocked.
3. Log in and retry.
4. Confirm download succeeds and `download_event` is recorded.
