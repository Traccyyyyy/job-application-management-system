# Job Application Management System

A learning-focused Spring Boot REST API for tracking companies, job applications, and notes. PostgreSQL is the
required primary relational database. Phase 4 adds controlled Flyway migrations and an Oracle runtime profile for
the later APEX phase; this project is not production-ready.

## Data model and lifecycle

`Company` stores a unique normalized name, optional website and industry, and timestamps. `JobApplication` has a
required company, unique job URL, title, status, optional source/location/applied date, and timestamps.
`ApplicationNote` has required content (maximum 2000 characters), a creation timestamp, and a required application.

Notes are owned by the `JobApplication` aggregate. Hibernate `cascade = ALL` and `orphanRemoval = true` delete notes
before deleting their application. The database foreign key deliberately has no `ON DELETE CASCADE`: ORM cascade and
database cascade are different mechanisms, and the approved service lifecycle owns this operation. A company cannot
be deleted while applications reference it.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Plain-text health response |
| POST/GET | `/api/companies` | Create/list companies |
| GET | `/api/companies/{id}` | Get a company |
| POST/GET | `/api/applications` | Create or filter/search/sort/page applications |
| GET/PUT/DELETE | `/api/applications/{id}` | Get, update, or hard-delete an application |
| PATCH | `/api/applications/{id}/status` | Apply a valid status transition |
| GET | `/api/applications/summary` | Count all seven statuses |
| POST/GET | `/api/applications/{applicationId}/notes` | Create/list notes newest-first |

Application filters are `status`, `company`, and `keyword`. Supported sort fields are `createdAt`, `updatedAt`,
`appliedDate`, `jobTitle`, `status`, and `companyName`. Validation errors return 400, missing resources 404, and
duplicates or invalid transitions 409. Unexpected and database errors use safe messages without SQL details.

## Database responsibilities

| Database | Responsibility | Schema strategy |
|---|---|---|
| PostgreSQL | Required default runtime and primary persistence target | Flyway V1, then Hibernate `validate` |
| Oracle | Phase 5 APEX-compatible runtime target | Oracle Flyway V1, then Hibernate `validate` |
| H2 | Fast, isolated automated unit/slice/integration tests only | Explicit Hibernate `create-drop`; Flyway disabled |

H2 Oracle mode is retained only to keep the existing tests deterministic. It is not evidence of Oracle compatibility
and the PostgreSQL/Oracle migrations are not distorted to run on H2. Runtime profiles never use Hibernate to create or
update schema and Open EntityManager in View remains disabled.

## Flyway schema

Vendor migrations live under:

- `db/migration/postgresql/V1__create_job_tracker_schema.sql`
- `db/migration/oracle/V1__create_job_tracker_schema.sql`

The files are separated because identity and string type syntax genuinely differs. They represent the same schema and
are versioned, repeatable from an empty database, and contain no seed data. Flyway owns runtime schema creation;
Hibernate only validates entity compatibility afterward, causing startup to fail on migration or mapping drift.

V1 creates `companies`, `job_applications`, and `application_notes`; primary keys; required columns; unique normalized
company name and job URL constraints; and both foreign keys. The unique constraints already create indexes, so no
duplicate job URL index is added. Additional indexes are:

- `idx_job_application_status` for status filtering and summary grouping.
- `idx_job_app_applied_date` for applied-date sorting.
- `idx_job_application_company` for joins and company filtering.
- `idx_note_application_created` on `(application_id, created_at, id)` for parent lookup and stable note ordering.

All explicit identifiers stay within Oracle's portable 30-character identifier limit.

Inspect migration history with:

```bash
docker exec job-tracker-postgres psql -U job_tracker -d job_tracker \
  -c 'select installed_rank, version, description, success from flyway_schema_history order by installed_rank'
```

## PostgreSQL setup and run

Prerequisites: Java 17, Docker for the documented disposable database and container-backed test, and internet access on
the first dependency/image download.

```bash
docker run --name job-tracker-postgres \
  -e POSTGRES_DB=job_tracker \
  -e POSTGRES_USER=job_tracker \
  -e POSTGRES_PASSWORD=job_tracker \
  -p 5432:5432 -d postgres:17.6-alpine

export JOB_TRACKER_DB_URL=jdbc:postgresql://localhost:5432/job_tracker
export JOB_TRACKER_DB_USERNAME=job_tracker
export JOB_TRACKER_DB_PASSWORD=job_tracker
./mvnw spring-boot:run
```

The default profile is `postgres`; activate it explicitly with `SPRING_PROFILES_ACTIVE=postgres` if desired. Safe local
defaults match the example container. Override all three values outside local development. Never commit real passwords
or `.env` files.

| Profile | URL | Username | Password |
|---|---|---|---|
| `postgres` (default) | `JOB_TRACKER_DB_URL` | `JOB_TRACKER_DB_USERNAME` | `JOB_TRACKER_DB_PASSWORD` |
| `oracle` | `JOB_TRACKER_ORACLE_URL` | `JOB_TRACKER_ORACLE_USERNAME` | `JOB_TRACKER_ORACLE_PASSWORD` |
| `test` | fixed in-memory H2 URL | `sa` | empty non-secret test value |

## Oracle profile

The Oracle profile is intended for Phase 5 APEX integration and requires all three environment variables; there are no
credential defaults:

```bash
export JOB_TRACKER_ORACLE_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
export JOB_TRACKER_ORACLE_USERNAME=job_tracker
export JOB_TRACKER_ORACLE_PASSWORD='replace-locally'
SPRING_PROFILES_ACTIVE=oracle ./mvnw spring-boot:run
```

It uses the official `ojdbc11` driver, Oracle-specific Flyway database support, the Oracle migration location, and
Hibernate validation. The official Database Free registry advertised a compatible `amd64` image, but it was not
already installed and the large image download did not complete during verification; the pull was stopped before any
Oracle container or database existed. The profile and migration are therefore statically prepared, but real Oracle
runtime compatibility remains unverified. Do not interpret H2 results as Oracle verification.

## Configuration model

Spring loads `application.yaml`, then the active `application-{profile}.yaml`; environment variables and command-line
arguments override file values (command-line arguments have higher precedence). Profiles separate runtime databases
from deterministic tests, and secrets use environment variables so credentials do not enter source control.

`TimeConfig` is the project's practical `@Configuration`/`@Bean` example: it supplies a `Clock` that the status service
uses when assigning applied dates. `@Component` marks an application-owned class for component scanning; `@Bean`
registers an object created by a configuration method, which is useful for JDK or third-party types such as `Clock`.

## Build and test

Docker must be running because one focused integration test uses a shared disposable PostgreSQL 17.6 Testcontainers
instance. The remaining focused repository, service, controller, and application-flow tests stay on isolated H2.

```bash
./mvnw test
./mvnw clean verify
```

## Troubleshooting

- Wrong profile: check startup's active-profile line and set `SPRING_PROFILES_ACTIVE=postgres` or `oracle`.
- Unresolved Oracle variable: supply all three `JOB_TRACKER_ORACLE_*` values; placeholders intentionally have no defaults.
- Authentication failure: verify username/password against the target database without printing the secret.
- Connection refused: confirm the database is running, port mapping is correct, and the JDBC host is reachable.
- Migration failure: inspect the first Flyway error and `flyway_schema_history`; never enable Hibernate schema creation as a workaround.
- Schema-validation failure: confirm Flyway completed and that the selected vendor migration location matches the active profile.

## Verification and phase ledger

Phase 4 was verified on Docker Desktop 29.6.2 (`x86_64`) with PostgreSQL 17.6 Alpine, Flyway 12.4.0, PostgreSQL JDBC
42.7.11, Spring Boot 4.1.0, Hibernate 7.4.1, and Java 17. An empty schema migrated to V1; Hibernate validation and the
packaged app started; HTTP creation, duplicate handling, filtering/paging/sorting, summary, transition, notes, restart
persistence, deletion, constraints, indexes, and Flyway history were checked. Oracle runtime verification was not run.

Completed through Phase 3: Company, JobApplication, ApplicationNote, layered REST API, aggregate note lifecycle,
validation and safe errors, filtering/pagination/sorting/summary, and H2-backed automated tests.

Completed in Phase 4: controlled Flyway migrations, default PostgreSQL runtime profile, real PostgreSQL persistence and
restart verification, PostgreSQL constraints/indexes, environment-variable database configuration, Oracle runtime
profile and vendor migration structure. Oracle runtime execution is not claimed.

Deferred: Phase 5 Oracle APEX frontend/integration; Phase 6 GitHub Actions and final evidence; authentication and
unrelated product expansion.

Known low-priority limitations remain: validation-message ordering is provider-dependent, pagination has no configured
maximum page size, whitespace-only optional website values are not normalized before validation, and Oracle runtime
behavior remains unverified. Hard deletion is a deliberate single-user MVP choice; audit history and soft deletion are
not included.
