# MyFitness — Gym Management System

A full-stack gym management platform with role-based access, real billing logic, and an AI assistant backed by a genuine retrieval-augmented generation (RAG) pipeline — built end-to-end as a portfolio project to demonstrate production-oriented backend and frontend engineering.

**Live app:** [myfitness-frontend.onrender.com](https://myfitness-frontend.onrender.com)
**API:** [myfitness-gym-management-system.onrender.com](https://myfitness-gym-management-system.onrender.com)

Demo credentials:
| Role | Username | Password |
|---|---|---|
| Admin | `demo_admin` | `DemoPass123` |
| Member | `dm01_demo` | `DemoPass123` |

> Hosted on Render's free tier — the first request after a period of inactivity may take 30–60 seconds to wake the server. This is a hosting-tier characteristic, not an application issue.

---

## Why this project

Most student portfolio CRUD apps stop at "create, read, update, delete." This one was built to go further in three specific directions: **real authorization boundaries** (not just hidden UI buttons), **real business logic** (derived billing state, not stored flags that can drift out of sync), and a **genuine RAG pipeline** for the AI assistant rather than a thin wrapper around a chat API.

---

## Tech Stack

**Backend**
- Java 21, Spring Boot 3.2.5
- PostgreSQL (Neon, serverless) via HikariCP connection pooling
- Spring Security + JWT (jjwt) for stateless authentication, BCrypt for password hashing
- Google Gemini API for chat, tool-calling, and embeddings
- pgvector for similarity search
- JUnit 5 — 120 tests across 21 test classes

**Frontend**
- React 19, Vite 8
- React Router 7
- Plain CSS Modules — no UI framework, hand-built design system with full light/dark theming

**Deployment**
- Backend: Render Web Service
- Frontend: Render Static Site
- Database: Neon (serverless Postgres)

---

## Architecture Highlights

### Layered authorization, mirrored on both ends
Every protected action is checked **twice**, independently: once in the React Router tree (`RoleRoute`/`ProtectedRoute` guards redirect unauthorized navigation before a page even renders) and once on the backend (role and ownership checks run server-side regardless of what the frontend does or doesn't show). A Member can never fetch another member's data by editing a URL or request payload — ownership is enforced at the service layer, not assumed from the UI.

### Billing state is derived, not stored
A membership's payment status (`PAID` / `DUE_SOON` / `OVERDUE`) is computed on read from the actual due date and current date — never written to the database as a flag. This was a deliberate choice: a stored status flag can silently drift out of sync with reality; a derived one can't.

### The AI Assistant is a real RAG pipeline, not a chat wrapper
Uploaded documents are chunked, embedded, and stored in pgvector. A query is embedded the same way, matched by similarity search, and the assistant answers only from retrieved chunks — with inline citations back to source material. Below a confidence threshold, it explicitly declines rather than guessing. Admin and Member document knowledge bases are kept separate, so a Member's assistant can never surface Admin-only content. A separate tool-calling mode lets the assistant query live bootcamp class data directly rather than relying on stale retrieved text.

### Connection pooling and query performance
The backend runs on HikariCP rather than a single hand-rolled connection — validated under a rapid-restart stress test that reproduced the exact failure pattern a naive connection setup hits under load. A related fix eliminated an N+1 query pattern in member/class lookups (originally 2N+1 queries per page load), replacing it with constant-time batch loading.

### Deployment: two independently hosted services
The frontend is a Vite static build; the backend is a separate Spring Boot service. CORS and API base URLs are both environment-driven, not hardcoded, so the same codebase runs identically in local development and production. Client-side routing required an explicit SPA fallback rewrite rule on the host — a detail that's easy to miss and worth calling out, since it's a common gap between "builds successfully" and "actually works when a user refreshes the page."

---

## Core Features

**Admin**
- Full CRUD for Members, Staff (Instructors / Full-time / Part-time), and Bootcamp Classes
- Revenue dashboard and Reports, computed live from real payment and membership data
- CSV export for Members and Revenue data
- Confirmation dialogs on every destructive action; toast feedback on every state-changing one
- Admin-scoped AI Assistant with its own document knowledge base

**Member**
- Personal dashboard, membership status, and billing history
- Fitness goal tracking
- Class browsing (enrollment is staff-managed, by design)
- Member-scoped AI Assistant

**Shared**
- JWT-based auth with BCrypt-hashed passwords
- Full light/dark theming across every page
- Three membership types (Standard, Student Saver, Pay As You Go), each with distinct billing rules and multi-class enrollment discounting

---

## API Overview

53 REST endpoints across Members, Staff, Bootcamp Classes, Memberships, Payments, Documents, AI, and Auth. Interactive API documentation is available via Swagger/OpenAPI at `/swagger-ui.html` on the backend once running.

---

## Running Locally

**Backend**
```bash
# Requires: Java 21, Maven, a PostgreSQL database (Neon or local)
git clone https://github.com/aqsak-dev99/MyFitness-Gym-Management-System.git
cd MyFitness-Gym-Management-System

# Set required environment variables:
export DATABASE_URL=<your-postgres-connection-string>
export GEMINI_API_KEY=<your-gemini-api-key>
export JWT_SECRET=<any-long-random-string>

mvn clean package
java -jar target/myfitness.jar
```
Backend runs on `http://localhost:8080`.

**Frontend**
```bash
cd frontend
npm install
npm run dev
```
Frontend runs on `http://localhost:5173` and talks to `localhost:8080` by default — no extra configuration needed for local development.

---

## Testing

```bash
mvn clean package
```
119 tests covering service-layer business logic, repository behavior, and billing/membership edge cases.

---

## License

MIT