# AUTH MODEL

## Overview

This project uses Microsoft Entra ID (OIDC) for user sign-in and signup.

- No manual self-registration form is used for account creation.
- `/register` is an SSO entry point (`Sign up with Microsoft`).
- Accounts are auto-provisioned on first successful Microsoft login.

## Role Mapping Rules

Email is normalized to lowercase + trimmed before role mapping.

1. `@my.sampoernauniversity.ac.id` -> `STUDENT`
2. `@sampoernauniversity.ac.id` -> default `LECTURER`
3. `@sampoernauniversity.ac.id` + allowlisted email -> `ADMIN`
4. Any other domain -> rejected (`domain_not_allowed`)

## Admin Allowlist

Admins share the same staff domain as lecturers, so domain-only mapping is not enough.

Admin assignment uses `ADMIN_EMAILS` environment variable:

```bash
export ADMIN_EMAILS="feoni.karismawati@sampoernauniversity.ac.id,libadmin@sampoernauniversity.ac.id"
```

The list is parsed as comma-separated emails, normalized to lowercase, and checked during SSO provisioning.

## Onboarding Policy

- `STUDENT` and `LECTURER` must complete onboarding (`/onboarding`) before accessing protected workflow pages.
- `ADMIN` is always considered profile-complete and can go directly to admin dashboard.
