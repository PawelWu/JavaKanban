# JavaKanban
Kanban using java, time tracking, weekly summary, subtasks

A full-stack Kanban board application with drag & drop, task CRUD, priority levels, and deadlines — backed by SQLite and served via Java Spring Boot.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17 + Spring Boot 3.2 |
| Database | SQLite (via JDBC) |
| Persistence | Spring Data JPA / Hibernate |
| Frontend | Vanilla HTML/CSS/JS (served statically) |

## Prerequisites

- **Java 17+** (OpenJDK or Oracle JDK)
- **Maven 3.8+**

## Build & Run

```bash
# From project root:
mvn clean package -DskipTests
java -jar target/kanban-board-1.0.0.jar
```

The server starts on **http://localhost:8080**. Open that URL in your browser.

### Alternative: Run with Spring Boot DevTools (hot reload)

```bash
mvn spring-boot:run
```

This enables automatic restart when source files change.

## Database

The SQLite database file `kanban-board.db` is created automatically on first run, next to the application JAR or in your working directory if running via `mvn spring-boot:run`.

No separate database setup needed — everything works out of the box.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/columns` | List all columns with their tasks |
| POST | `/api/columns` | Create a new column |
| PATCH | `/api/columns/:id` | Rename a column |
| DELETE | `/api/columns/:id` | Delete a column (and its tasks) |
| POST | `/api/columns/:colId/tasks` | Create a task in a column |
| GET | `/api/columns/:colId/tasks/:id` | Get a specific task |
| PUT | `/api/columns/:colId/tasks/:id` | Update a task |
| DELETE | `/api/columns/:colId/tasks/:id` | Delete a task |
| PATCH | `/api/columns/:srcColId/move` | Move task to another column (body: `{"taskId":"...","targetColumnId":"..."}`) |
| PATCH | `/api/columns/:colId/tasks/:id/reorder` | Reorder task within column (body: `{"displayOrder":0}`) |

## Frontend Features

- **Drag & drop** — move tasks between columns and reorder within columns
- **Task CRUD** — create, edit, delete tasks via double-click or context menu (`⋮`)
- **Priority levels** — Low (🟢), Medium (🟡), High (🟠), Critical (🔴) with color-coded border bars and badges
- **Deadlines** — automatic status detection (Today / Overdue / Upcoming)
- **Column management** — rename columns by clicking the title, add/delete columns

## Project Structure

```
├── pom.xml                    # Maven build config
├── README.md                  # This file
├── index.html                 # Frontend HTML
├── style.css                  # Frontend styles
├── app.js                     # Frontend logic (API calls)
└── src/main/java/com/kanban/
    ├── KanbanApplication.java           # Spring Boot entry point
    ├── config/
    │   ├── DataSourceConfig.java        # JDBC driver (package-private helper)
    │   ├── JpaConfig.java               # EntityManagerFactory + HikariCP for SQLite
    │   └── SQLiteDialect.java           # Custom Hibernate dialect for SQLite
    ├── entity/
    │   ├── Column.java                  # Column entity
    │   ├── Task.java                    # Task entity
    │   └── Priority.java                # Priority enum (LOW/MEDIUM/HIGH/CRITICAL)
    ├── repository/
    │   ├── ColumnRepository.java        # JPA repository for columns
    │   └── TaskRepository.java          # JPA repository for tasks
    ├── service/
    │   └── KanbanService.java           # Business logic layer
    ├── controller/
    │   └── KanbanController.java        # REST API endpoints
    ├── dto/
    │   ├── ColumnRequest.java           # Create column request DTO
    │   ├── ColumnResponse.java          # Column response DTO (with tasks)
    │   ├── TaskRequest.java             # Create/update task request DTO
    │   └── TaskResponse.java            # Task response DTO
    └── exception/
        └── GlobalExceptionHandler.java  # REST error handling
```
