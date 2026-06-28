# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Personal finance app ("Mis Finanzas"): Spring Boot 4 (Java 25) backend + Angular 20 frontend + PostgreSQL 17. UI text, README, and commit context are in Spanish.

## Product docs (PRDs) — mandatory maintenance

**Rule: every code change automatically creates or updates the project documentation.** Whenever you modify code, you must, in the same change, update the affected domain's PRD (or create it if it does not exist yet) so the docs never drift from the code. This is not optional.

Per-domain PRDs live in `docs/prd/` (index and template in `docs/README.md`), written in Spanish. A change "affects a domain" when it touches its data model, business rules, API endpoints, or UI. Concretely: when you finish a code change, identify which domain(s) under `docs/prd/` it touches, and edit the matching PRD(s) — bump "Última actualización", and adjust the relevant sections (model, rules, API, UI, validations). If no PRD exists for the affected domain, create one following the existing template.

## Git workflow — commit every change

This is a **local git repository with no remote** (the user manages the remote, if any). The user has asked that **every change we make be committed**: when you finish a logical unit of work (code + its PRD update), create a commit for it. Group related edits into one coherent commit with a clear Spanish message. Do not push (there is no remote). Commit messages are in Spanish, matching the project's language.

**Do not add the `Co-Authored-By: Claude ...` trailer** to commit messages (explicit user preference).

## Commands

```bash
./app.sh start|stop|restart|status [db|backend|frontend]   # manage all services (logs/PIDs in .run/)
```

On first boot against an empty DB the app seeds the **default global categories** only (no demo accounts/movements).

Manual equivalents:

```bash
docker compose up -d                  # PostgreSQL on :5432
cd backend && mvn spring-boot:run     # API on :8080
cd frontend && npx ng serve           # UI on :4200, proxies /api to :8080 (proxy.conf.json)
```

- Backend tests: `cd backend && mvn test` (single test: `mvn test -Dtest=ClassName#method`). The suite (domain unit tests, application-service tests with mocked ports, `@DataJpaTest` persistence-adapter tests on real PostgreSQL via Testcontainers, `@WebMvcTest` contract tests, and an ArchUnit boundary test) is the migration's safety net; keep it green and coverage ≥ 99 %.
- Frontend tests: `cd frontend && npm test` (Karma/Jasmine; only `app.spec.ts` exists).
- Frontend build: `cd frontend && npm run build`.
- Reset DB: `./app.sh stop && docker compose down -v`, then `./app.sh start` (seeds default categories only).

## Architecture

### Backend (`backend/src/main/java/com/xroig/finance/`)

**Hexagonal (ports & adapters) + DDD**, one Maven module organized by **bounded context** and, within each, by hexagonal layer (see `docs/migration-ddd-hexagonal.md` for the migration history). Contexts: `accounts`, `categories`, `transactions`, `transfers`, `budgets`, `categorization`, `imports`, `reporting`, plus `shared` (kernel). Per context:

- `domain/` — pure aggregates (POJOs/records), value objects (`Money`, typed ids like `AccountId`/`CategoryId`, `MonthsMask`, `TransactionType`…) and **outbound ports** (`XRepository`, query/usage ports). No Spring, no JPA. Invariants live here (e.g. `Transaction` refund rules, `Category` one-level hierarchy, `Transfer` origin≠destination, `Budget` month/amount).
- `application/` — `XService` implementing the **inbound ports / use cases** (`CreateX`/`UpdateX`/`FindX`…), orchestrating outbound ports. Read-only screens use **CQRS**: `XQueryPort` + read-model records (`XView`) instead of rebuilding aggregates.
- `infrastructure/persistence/` — `XJpaEntity` (mapped to the Flyway tables), `XJpaRepository` (Spring Data), `XPersistenceAdapter`/`XQueryAdapter` implementing the ports, and `XJpaMapper` (domain↔entity). Aggregates reference each other by id, so `@ManyToOne` associations are resolved via `getReferenceById`.
- `infrastructure/web/` — thin `XController` (delegates to inbound ports) + web DTOs (`XRequest`/`XResponse`).

Cross-cutting pieces:

- `shared/domain` — kernel: `Money`, `DateRange`, `TransactionType`, `TextNormalizer`, the `DomainException` hierarchy (`NotFound`/`Conflict`/`Validation`). `shared/web` — `DomainExceptionHandler` (maps `DomainException`→404/409/400 as `problem+json`) and `DataIntegrityExceptionHandler` (last-resort unique-constraint→409). The domain never knows `HttpStatus`.
- `reporting` — dashboard, **read-only/CQRS**: `ReportingService` keeps the aggregation maths and reads raw figures through outbound query ports (`/api/dashboard/{summary,monthly,monthly-balance,income-by-category,expenses-by-category,by-account,budgets}`).
- `imports` — CSV/Excel (`.xls`/`.xlsx`) bank-export import via Apache POI + commons-csv. `ImportFileParser` is an **anti-corruption layer** (`ImportFileReader`) translating bank rows to the `ImportRow` VO; the `ImportService` use case **reuses** the Transactions/Transfers/Categories use cases via bridge adapters. Deliberately tolerant: detects the header row after bank preambles, accepts `dd/MM/yyyy` or ISO dates, `1.234,56` or `1234.56` amounts, `,` or `;` separators, accent-insensitive headers; a "Fecha de operación: …" inside the "Más datos" column overrides the date column. Unknown categories are auto-created; accounts must already exist; per-row errors are reported back, valid rows imported.
- `categorization` — `CategoryRule` aggregate (pattern alternatives separated by `|`, case/accent-insensitive via the `PatternMatcher` domain service) auto-categorizes imported transactions lacking a category column; fallback "Otros gastos"/"Otros ingresos".
- `config/DataSeeder` — startup bootstrap (the one class outside the layered packages): on first boot against an empty DB it seeds the default global categories (only) by driving the categories context's `CreateCategory` use case; idempotent via the context's "is it empty?" read.

The direction of dependencies (domain ← application ← infrastructure; web never touches persistence) is fenced by **ArchUnit** (`architecture/ArchitectureTest`, archunit-junit5 1.4.2 — older versions silently fail to parse Java 25 bytecode); the single module does not enforce it at compile time.

Categories support one level of subcategories (`Category.parent`, self-reference; migration `V4`). A subcategory inherits its parent's `type`; it inherits the account scope only when the parent is account-bound — a global parent may have global *or* account-specific subcategories (decided in `categories/application/CategoryService`). A top-level category can only be reassigned to a concrete account if all its subcategories already belong to that same account (otherwise its global/cross-account children would be orphaned). Only one level is allowed (a subcategory cannot have children). Movements may attach to either a top-level category or a subcategory. Aggregations roll subcategories up to their parent: `TransactionJpaRepository.sumByCategory` groups by `coalesce(parent.id/name, …)`, and budget "spent" on the dashboard uses `sumByCategoryTreeAndPeriod`. Budgets sit on leaf categories (a top-level category without subcategories, or a subcategory); a category with subcategories acts as the read-only aggregate of its children in the annual matrix and budgeting it directly is rejected. The annual matrix reads per-exact-category sums (`sumByExactCategoryAndMonthOfYear`) and aggregates parents in `budgets/.../BudgetQueryAdapter` (the read-side CQRS adapter). Category name uniqueness is per scope (account) and per parent.

Domain rules to preserve: account balances are computed (initial balance + transactions), never stored; transfers affect balances but are excluded from income/expense aggregates; accounts/categories with movements cannot be deleted (and a category with subcategories cannot be deleted); budgets are per leaf category (income or expense) per account per month. The budgets screen is an annual matrix (`GET /api/budgets/annual?year=&accountId=`) mirroring the user's spreadsheet: 12 months × categories with planned/real/difference, plus TOTAL INGRESOS, TOTAL GASTOS, AHORRO, % and AHORRO ACUMULADO rows; a category with subcategories shows a read-only aggregate row with its subcategories nested as editable rows. Cells are editable inline (reusing the per-month budget endpoints) only for leaf rows and only when a concrete account is selected.

A leaf, account-bound category may declare a **recurrence** (`RecurringBudget` aggregate + effective-dated `RecurrenceAmount` VO, migration `V5`, managed from the category form via `/api/categories/{id}/recurrence`): active months as a 12-bit `MonthsMask` plus a history of amounts with a `validoDesde`. It only feeds the planned side of the matrix (never real movements). In `budgets/.../BudgetQueryAdapter` the planned value of a cell is the manual `Budget` if present (the inline-edited override always wins), otherwise the recurrence's amount in force for that month (latest `validoDesde` ≤ that month, via `RecurringBudget.plannedAmount`), otherwise 0. Global categories cannot have a recurrence, and a category with a recurrence can be neither made global nor given subcategories (enforced in `categories/application/CategoryService`).

### Database

Schema is owned by Flyway (`backend/src/main/resources/db/migration/`); Hibernate runs with `ddl-auto=validate`. Any schema change requires a new `V<n>__*.sql` migration — never edit existing migrations or rely on Hibernate to alter tables. Connection settings come from `FINANCE_DB_*` env vars (defaults match docker-compose: finance/finance/finance).

### Frontend (`frontend/src/app/`)

Standalone components, lazy-loaded per page via `app.routes.ts` (`pages/dashboard|transactions|transfers|budgets|accounts|categories`). All HTTP goes through the single `ApiService` (`api.service.ts`) with shared interfaces in `models.ts`. Charts use Chart.js directly (no wrapper lib). The reusable import dialog lives in `components/import-dialog.ts`. Prettier config is in `package.json` (printWidth 100, single quotes, Angular HTML parser).

Theming: light/dark theme via `ThemeService` (`theme.service.ts`), toggled from the sidebar and persisted in `localStorage` under `theme`. It sets `data-theme` on `<html>`; dark overrides for the CSS variables live in `styles.scss` under `:root[data-theme='dark']`. Chart.js charts read `ThemeService.chartText()/chartGrid()` and redraw on theme change (an `effect` in `DashboardPage`). The sidebar can also be collapsed (persisted under `sidebar-collapsed`).
