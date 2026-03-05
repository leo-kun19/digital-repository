# admin-ui

React + TypeScript + Vite frontend for the case-based digital repository workflow.

## Run locally

From project root, start backend first:

```bash
docker compose up -d
./mvnw spring-boot:run
```

Then run frontend:

```bash
cd admin-ui
npm install
npm run dev
```

Vite runs at `http://localhost:5173` and proxies `/api` requests to `http://localhost:8080`.

## Build

```bash
cd admin-ui
npm run build
```

## Main routes

- Public: `/`, `/repo/:id`, `/login`, `/register`
- Student: `/student/dashboard`, `/student/registrations`, `/student/registrations/new`, `/student/cases/:caseId`, `/student/cases/:caseId/submission`, `/student/clearance`, `/student/clearance/:caseId`
- Lecturer: `/lecturer/dashboard`, `/lecturer/approvals`, `/lecturer/review`, `/lecturer/students`
- Admin: `/admin/dashboard`, `/admin/review`, `/admin/review/:caseId`, `/admin/clearance`, `/admin/publish`, `/admin/checklists`, `/admin/repository`
