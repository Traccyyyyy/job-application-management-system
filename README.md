# Job Application Management System

A learning-focused Spring Boot REST API for tracking companies, job applications, and application notes. Phase 3 provides
the relational backend foundation; it is not production-ready.

## Phase 3 scope and data model

`Company` stores a name, optional website and industry, timestamps, and has a lazy one-to-many
relationship to `JobApplication`. Each application owns a required lazy many-to-one `company_id`
relationship and stores its title, unique URL, status, optional source/location, applied date and timestamps.
`ApplicationNote` stores an ID, required content (maximum 2000 characters), and creation timestamp. It owns a
required lazy many-to-one relationship through the non-null `application_id` foreign key; `JobApplication` has
the inverse lazy one-to-many relationship.

Requests flow from controllers through transactional services, which apply business rules, to Spring Data
repositories. Manual mappers isolate JPA entities from request and response DTOs. A global exception handler
returns consistent JSON errors.

Notes are part of the `JobApplication` aggregate and have no independent lifecycle. The inverse relationship uses
`cascade = ALL` and `orphanRemoval = true`, so hard-deleting an application removes its notes in the same service
transaction and no orphan note rows remain. Neither relationship is exposed recursively in JSON or made eager.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | Plain-text health response |
| POST | `/api/companies` | Create a company |
| GET | `/api/companies` | List companies by name |
| GET | `/api/companies/{id}` | Get a company |
| POST | `/api/applications` | Create an application |
| GET | `/api/applications/{id}` | Get an application |
| GET | `/api/applications` | Filter, search, sort and paginate |
| PUT | `/api/applications/{id}` | Update editable fields (never status) |
| PATCH | `/api/applications/{id}/status` | Apply a valid status transition |
| DELETE | `/api/applications/{id}` | Hard-delete an application |
| GET | `/api/applications/summary` | Count every application status |
| POST | `/api/applications/{applicationId}/notes` | Create a note for an application |
| GET | `/api/applications/{applicationId}/notes` | List an application's notes |

Application list filters are `status`, `company`, and `keyword`; paging defaults to page 0 and size 20.
Allowed sort fields are `createdAt`, `updatedAt`, `appliedDate`, `jobTitle`, `status`, and `companyName`.
Notes are not paginated in Phase 3 and are ordered in the database by `createdAt DESC`, then `id DESC` for a
stable newest-first result. Creating a note returns 201 and a Location header ending in `/notes/{noteId}`;
that individual-note URI intentionally has no GET endpoint in this phase.

## Validation and errors

Names and titles are required and length-limited. URLs must start with HTTP or HTTPS. Applied dates cannot
be in the future. Company names are unique ignoring case and job URLs are unique. Invalid requests return
400, missing resources return 404, and duplicates or invalid status transitions return 409. Error JSON has
`timestamp`, `status`, `error`, `message`, `path`, and validation `fieldErrors`.
Note content is required, cannot be blank, and cannot exceed 2000 characters; these rules are enforced at the
request and database-column layers. Unknown fields follow strict JSON handling. Missing parent applications return
404, malformed JSON returns 400, database errors do not expose SQL details, and unexpected failures use a safe 500.

New applications default to `SAVED`; only `SAVED` and `APPLIED` are valid initial states. The supported path
is SAVED to APPLIED, then APPLIED to SCREENING, then SCREENING to INTERVIEW, with the specified rejection,
withdrawal, and offer terminal paths. An APPLIED application receives today's date when none is supplied.

## Run and verify

```bash
./mvnw spring-boot:run
./mvnw test
./mvnw clean verify
```

The default `local` profile uses file-backed H2 under `local-db/`, so local data survives restarts. Tests use
the isolated in-memory `test` profile. Both use H2 Oracle compatibility mode where practical. This mode is
not evidence that the application has run on Oracle; real Oracle schema and runtime verification are deferred.
Future Oracle credentials will be supplied through environment variables in an Oracle profile.

```bash
curl -i -X POST http://localhost:8080/api/companies \
  -H 'Content-Type: application/json' \
  -d '{"name":"Example Co","website":"https://example.com","industry":"Technology"}'

curl -i -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{"jobTitle":"Backend Engineer","jobUrl":"https://example.com/jobs/1","companyId":1}'

curl -i -X PATCH http://localhost:8080/api/applications/1/status \
  -H 'Content-Type: application/json' -d '{"status":"APPLIED"}'

curl -i -X POST http://localhost:8080/api/applications/1/notes \
  -H 'Content-Type: application/json' \
  -d '{"content":"Followed up with the recruiter."}'

curl -i http://localhost:8080/api/applications/1/notes
```

Hard deletion is a deliberate single-user MVP choice; audit history and soft deletion are not included.

## Verification state and limitations

The Maven tests, packaged application, focused note test slices, and representative live HTTP flow are the Phase 3
verification target. This phase has no authentication, multi-user ownership, note update/delete API, migration tooling, production database,
PostgreSQL or Oracle execution, APEX UI, or CI workflow.

## Phase ledger

Completed in Phase 2: Company, JobApplication, Company/Application relationship, core REST API, filtering, sorting
and summary, validation/errors, H2-backed JPA persistence, and automated tests.

Completed in Phase 3: ApplicationNote, JobApplication/ApplicationNote relationship, create-note and list-notes
endpoints, note validation, parent aggregate deletion behavior, and automated note tests.

Deferred:

- Phase 4: PostgreSQL and Oracle schema/profile work and real database verification
- Phase 5: Oracle APEX
- Phase 6: GitHub Actions and final evidence/documentation

Accepted low-priority Phase 2 limitations: validation-message ordering remains provider-dependent, pagination has
no configured maximum page size, and whitespace-only optional website values are not normalized before validation.
