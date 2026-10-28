# AGENTS.md — Project Knowledge & Pitfalls

## Database / SQLite

- **SQLite file locking**: Use single connection pool (`HikariCP` default is fine since it pools connections on the same file). Avoid concurrent writers.
- **No transaction isolation bugs**: Since all reads/writes go through one Hikari pool, deadlocks are extremely rare.
- **Never inspect/modify the `.db` file while the server is running** — SQLite locks the file and external modifications will corrupt state.

## JPA / Hibernate

- **Class-level `@Transactional(readOnly = true)` disables auto-flush.** Any write method MUST override with a dedicated `@Transactional()` annotation, or changes execute silently (no exception, no commit).
- **`orphanRemoval = true` on `@OneToMany` will DELETE entities** when removed from the parent's collection. This was how tasks got lost during drag-and-drop moves. Keep it `false` or use only `CascadeType.PERSIST`.
- **Changing the FK on a child entity (`task.setBoardColumn(target)`) is sufficient for cross-entity moves.** Do NOT also mutate the source collection (that triggers orphanRemoval deletion).
- **`Repository.delete()`/`deleteById()` on a child whose FK is `nullable = false` is a SILENT NO-OP.** `DELETE /api/columns/{c}/tasks/{t}` returns 204 and leaves the row in `tasks`; no exception, nothing in the log. Deleting the *owner* (column) works because JPA cascades through the non-nullable FK. Fixing the child delete needs `orphanRemoval = true` on the owning collection (breaks drag-and-drop unless guarded) or a nullable FK plus explicit child deletes — deleting the `TimeLog` children first is **not** sufficient.
- **Unlink from every EAGER collection before deleting, too.** Deleting a Task that owns Subtasks/TimeLogs is a silent no-op unless you first call remove() on `task.getSubtasks()`, `task.getTimeLogs()` AND `col.getTasks()` for the entities about to be deleted, then the deleteById calls. Same for a Subtask: `task.getSubtasks().remove(sub)` before `subtaskRepo.deleteById(...)`. EAGER fetch keeps the deleted entity referenced in memory, which blocks the owner's delete; remove() is memory-only (no orphanRemoval), so the explicit deleteById still does the real DELETE. Verified: without the unlink, DELETE task returned 204 and the task stayed on the board.
- **`@PATCH` and `@DELETE` annotation markers do not compile here** ("cannot find symbol: class PATCH"). Use the method annotations `@PatchMapping` / `@DeleteMapping` like the rest of `KanbanController` does.
- **Hibernate 6 removed custom dialect `registerColumnType()`.** Use `org.hibernate.community.dialect.SQLiteDialect` from the `hibernate-community-dialects` dependency instead.
- **Entity class named `Column` conflicts with `jakarta.persistence.Column` annotation.** Rename to `BoardColumn` (or similar) to avoid compile errors like "cannot be converted to Annotation".

## Java "global namespace" sources (javac resolution)

- **`java.util.List`, `java.util.Map`, `java.util.Comparator`, `java.util.Integer` live in `java.util`,
  but date/time and boxed-number helpers do NOT.** Use `java.time.LocalDate`, `java.time.LocalDateTime`,
  `java.time.Duration`, `java.time.LocalDateTime` when fully qualified. Writing `java.util.LocalDate`
  is a compile error ("cannot find symbol class LocalDate location: package java.util").
- **`java.util.Comparator.comparing()` has only two overloads:** key-extractor (1 param) and
  key-extractor + comparator. A 2-arg lambda `(a, b) -> a.compareTo(b)` does NOT compile. Use a 1-arg
  key extractor instead: `sort(java.util.Comparator.comparing((java.time.LocalDate a) -> a))`, or negate
  for descending: `comparing((TaskDaySummary t) -> -t.getTotalSeconds())`.
- `java.time.LocalDateTime` has no `diffInDays`; compute day spans from `java.time.LocalDate` values
  (or loop with `plusDays`) — see `KanbanService.daySpan`.

## Static assets & browser cache

- **Static files are served from inside the JAR**, not from `src/main/resources` on disk: any change to
  `index.html` / `style.css` / `app.js` / `time.html` needs `mvn clean package` (use `clean`, otherwise
  the spring-boot `repackage` step can leave a 56 KB plain jar) and a server restart before it is visible.
- **Spring's static handler sends `Last-Modified: Fri, 01 Feb 1980` (the jar entry date, constant), no
  `ETag`, no `Cache-Control`.** Heuristic freshness = (now − LastModified) × 10% ≈ 4 years, so the browser
  reuses the previously cached `time.html`/`app.js`/`style.css` **without issuing a request** — a normal
  reload does not refetch. Symptom: API returns new data, page keeps rendering the old layout.
  Fixes: bump the `?v=N` query on the links in `index.html` (`style.css?v=3`, `app.js?v=3`,
  `time.html?v=3`) and in `time.html`'s own stylesheet link, or hard-refresh (Ctrl+F5).
- Verify what is actually served before blaming the page: `curl -s -o /dev/null -w "%{size_download}\n" "http://localhost:8080/time.html?v=3"` should equal `wc -c` of the source file, and `curl -s http://localhost:8080/index.html | grep -o "app.js?v=3"` should hit.

## Drag & Drop (Frontend)

- `dragover` fires ~60 times/sec during a drag. Always guard DOM mutations — check if the preview already exists in the correct position before calling `appendChild`/`insertBefore`.
- `handleDragLeave`'s bounds check must guard BOTH the class removal AND the preview cleanup, otherwise moving between adjacent columns causes previews to flicker or vanish.
- All CSS transitions/animations can cause layout thrashing during drag operations. Remove them where not needed; keep only modal animations and placeholder hover.

## Project Structure (Current)

```
src/main/java/com/kanban/
├── KanbanApplication.java           # Spring Boot entry point
├── config/
│   └── JpaConfig.java               # EntityManagerFactory + community dialect
├── entity/
│   ├── BoardColumn.java             # Column entity (renamed from Column)
│   ├── Task.java                    # Task entity (owns subtasks + timeLogs, both EAGER)
│   ├── Subtask.java                 # Subtask entity — one level below Task only
│   ├── TimeLog.java                 # Time tracking entity
│   └── Priority.java                # Enum: LOW/MEDIUM/HIGH/CRITICAL
├── repository/
│   ├── ColumnRepository.java        # → BoardColumnRepo is an alias; maps to columns table
│   ├── TaskRepository.java
│   ├── SubtaskRepository.java
│   └── TimeLogRepository.java
├── service/
│   └── KanbanService.java           # Business logic — all writes need @Transactional() override
├── controller/
│   └── KanbanController.java        # REST endpoints
├── dto/
│   ├── ColumnRequest.java / ColumnResponse.java
│   ├── TaskRequest.java / TaskResponse.java   # TaskResponse carries subtasks + counts
│   ├── SubtaskRequest.java / SubtaskResponse.java
│   └── TimeLogRequest.java / TimeLogResponse.java / TimeSummaryResponse.java ...
└── exception/
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.properties           # SQLite DB path, Hibernate props
└── static/
    ├── index.html                   # Frontend page
    ├── style.css                    # Styles (no transitions; cursor: default globally)
    └── app.js                       # API client + drag-and-drop

src/test/java/com/kanban/            # Tests directory (placeholder)
```

**Note:** `DataSourceConfig.java` and `SQLiteDialect.java` were removed — datasource is auto-configured by Spring Boot, and dialect uses the community package.

## Subtask feature (added)

- Entities: `Subtask` (child of `Task`, one level deep only) + `subtasks` table with non-nullable FK `ownerTask_id`. `Task.subtasks` is `@OneToMany(mappedBy = "ownerTask", cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)` with `@OrderBy("displayOrder ASC")`.
- Endpoints (all under a task, ownership validated by `getSubtaskOrThrow`): POST `/api/columns/{c}/tasks/{t}/subtasks` (create), GET same path (list), PUT `.../subtasks/{id}` (rename), PATCH `.../subtasks/{id}/toggle` (flip; body is `Map<String,String>` with optional `done` = "true"/"false" so `@NotBlank` on title is not triggered), DELETE `.../subtasks/{id}`.
- `TaskResponse` now carries `subtasks`, `subtaskCount`, `subtaskDoneCount`, so the board payload renders the checklist with no extra requests.
- Frontend: checklist rows inside the card (click a row to toggle), `☑ n/m` progress badge, `☑` button opening the `subtask-modal` (add a row / toggle / delete existing rows), plus a `Subtasks` item in the task context menu. Menu geometry constant `MENU_HEIGHT` in app.js had to grow (120 to 150) when the menu gained a 4th button.

## Build / Run Cycle (order matters)

- Kill the server BEFORE `mvn clean`: the running JVM maps `target/kanban-board-1.0.0.jar`, so `clean` dies with "Failed to delete ...jar" and the stale jar is reused. Sequence: `_kill_server.ps1`, `mvn -q clean package`, `_start_server.ps1`, sleep 7, curl 200 check.
- `_start_server.ps1` now redirects to `_server_out.log` / `_server_err.log`; a curl result of `code=000` means the JVM is gone or never bound 8080 - a stale java PID can linger, so kill all java and start again rather than assuming the old one is live.
- The write/edit tools mangle Java in this repo: they dropped the `public` modifier on entity setter lines and rewrote `java.util.ArrayList` to bare `ArrayList`. Grep the file back after every write; patch with awk/sed when it happens (bare `ArrayList` does compile, but that is not project style).

5. **Adding an element to `index.html`?** Add the matching `const x = document.getElementById(...)` in `app.js`'s declaration block *before* writing any top-level `x.addEventListener(...)`. `app.js` is one script that runs top to bottom: a ReferenceError in the declarations aborts `loadBoard()` and the board renders nothing (only static header buttons visible), with no error in the page itself.

## Frontend JS debugging (no browser console needed)

- `node --check app.js` only validates syntax; it will not catch a ReferenceError on a missing `getElementById` declaration.
- Run the file in node with a stubbed `document` (`vm.runInContext`, `getElementById` returns a stub element with `appendChild`/`append`/`querySelectorAll`/`classList`, `fetch` returns a canned board payload). It executes the whole script and prints the exact failing line, e.g. `at app.js:808` for `subtaskForm.addEventListener`. Keep such a harness (`_smoke_appjs.mjs`) after writing new UI code.
- Static files are served from inside the jar, so any `app.js`/`style.css`/`index.html` change needs `_kill_server.ps1` -> `mvn -q clean package` -> `_start_server.ps1`, plus the `?v=N` bump in `index.html` (and in `time.html`'s own stylesheet link) so the browser refetches. Verify with `curl -s -o /dev/null -w "%{size_download}" "http://localhost:8080/app.js?v=4"` against `wc -c` of the source file.

# DOX framework

- DOX is highly performant AGENTS.md hierarchy installed here
- Agent must follow DOX instructions across any edits

## Core Contract

- AGENTS.md files are binding work contracts for their subtrees
- Work products, source materials, instructions, records, assets, and durable docs must stay understandable from the nearest applicable AGENTS.md plus every parent AGENTS.md above it

## Read Before Editing

1. Read the root AGENTS.md
2. Identify every file or folder you expect to touch
3. Walk from the repository root to each target path
4. Read every AGENTS.md found along each route
5. If a parent AGENTS.md lists a child AGENTS.md whose scope contains the path, read that child and continue from there
6. Use the nearest AGENTS.md as the local contract and parent docs for repo-wide rules
7. If docs conflict, the closer doc controls local work details, but no child doc may weaken DOX

Do not rely on memory. Re-read the applicable DOX chain in the current session before editing.

## Update After Editing

Every meaningful change requires a DOX pass before the task is done.

Update the closest owning AGENTS.md when a change affects:

- purpose, scope, ownership, or responsibilities
- durable structure, contracts, workflows, or operating rules
- required inputs, outputs, permissions, constraints, side effects, or artifacts
- user preferences about behavior, communication, process, organization, or quality
- AGENTS.md creation, deletion, move, rename, or index contents

Update parent docs when parent-level structure, ownership, workflow, or child index changes. Update child docs when parent changes alter local rules. Remove stale or contradictory text immediately. Small edits that do not change behavior or contracts may leave docs unchanged, but the DOX pass still must happen.

## Hierarchy

- Root AGENTS.md is the DOX rail: project-wide instructions, global preferences, durable workflow rules, and the top-level Child DOX Index
- Child AGENTS.md files own domain-specific instructions and their own Child DOX Index
- Each parent explains what its direct children cover and what stays owned by the parent
- The closer a doc is to the work, the more specific and practical it must be

## Child Doc Shape

- Create a child AGENTS.md when a folder becomes a durable boundary with its own purpose, rules, responsibilities, workflow, materials, or quality standards
- Work Guidance must reflect the current standards of the project or user instructions; if there are no specific standards or instructions yet, leave it empty
- Verification must reflect an existing check; if no verification framework exists yet, leave it empty and update it when one exists

Default section order:
- Purpose
- Ownership
- Local Contracts
- Work Guidance
- Verification
- Child DOX Index

## Style

- Keep docs concise, current, and operational
- Document stable contracts, not diary entries
- Put broad rules in parent docs and concrete details in child docs
- Prefer direct bullets with explicit names
- Do not duplicate rules across many files unless each scope needs a local version
- Delete stale notes instead of explaining history
- Trim obvious statements, repeated rules, misplaced detail, and warnings for risks that no longer exist

## Closeout

1. Re-check changed paths against the DOX chain
2. Update nearest owning docs and any affected parents or children
3. Refresh every affected Child DOX Index
4. Remove stale or contradictory text
5. Run existing verification when relevant
6. Report any docs intentionally left unchanged and why

## User Preferences

When the user requests a durable behavior change, record it here or in the relevant child AGENTS.md

## Child DOX Index

This project is not yet indexed. Before continuing you must scan the project, build the DOX tree and replace this message with the actual index. Go deep and scan files recursively to properly evaluate complexity and create nested DOX files where needed.