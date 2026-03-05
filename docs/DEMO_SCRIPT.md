# Demo Script (FYP Defense)

## Pre-demo checks

1. Backend running (`./run-postgres.sh` or `./run-dev.sh`).
2. Frontend running (`cd admin-ui && npm run dev`).
3. `ADMIN_EMAILS` includes your library admin account.

## Walkthrough

1. Open public repository search (`/`).
2. Filter by:
   - Faculty
   - Study Program
   - Year Published
3. Open one item detail page and show metadata + protected download button.
4. Go to `/register`, click **Sign up with Microsoft**.
5. Sign in with university account:
   - student domain -> student role
   - staff domain -> lecturer/admin depending on allowlist
6. First login lands on `/onboarding` (student/lecturer only).
7. Complete onboarding:
   - Full name
   - Faculty
   - Study Program
   - Student ID (students only)
8. Continue workflow (student):
   - create publication registration
   - submit for supervisor approval
9. Lecturer account:
   - approve registration
   - review submission
10. Admin account:
    - verify registration queue
    - checklist review
    - publish
11. Return to public repository search:
    - verify published item appears
    - verify study program is shown
12. Sign in and download published item to show protected access + audit logging.

## Fallback notes

- If OAuth fails, verify `AAD_*` and redirect URI first.
- If upload fails, verify file is valid PDF and under 5MB.
