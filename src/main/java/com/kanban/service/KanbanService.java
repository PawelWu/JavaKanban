package com.kanban.service;

import com.kanban.dto.ColumnRequest;
import com.kanban.dto.ColumnResponse;
import com.kanban.dto.TaskRequest;
import com.kanban.dto.TaskResponse;
import com.kanban.dto.TimeLogRequest;
import com.kanban.dto.TimeLogResponse;
import com.kanban.dto.TaskTimeSummary;
import com.kanban.dto.SubtaskRequest;
import com.kanban.dto.SubtaskResponse;
import com.kanban.dto.TimeSummaryResponse;
import com.kanban.dto.DaySummary;
import com.kanban.dto.TaskDaySummary;
import com.kanban.entity.BoardColumn;
import com.kanban.entity.Priority;
import com.kanban.entity.Task;
import com.kanban.entity.Subtask;
import com.kanban.entity.TimeLog;
import com.kanban.repository.ColumnRepository;
import com.kanban.repository.SubtaskRepository;
import com.kanban.repository.TaskRepository;
import com.kanban.repository.TimeLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class KanbanService {

    private final ColumnRepository columnRepo;
    private final TaskRepository taskRepo;
    private final TimeLogRepository timeLogRepo;
    private final SubtaskRepository subtaskRepo;

    public KanbanService(ColumnRepository columnRepo, TaskRepository taskRepo, TimeLogRepository timeLogRepo, SubtaskRepository subtaskRepo) {
        this.columnRepo = columnRepo;
        this.taskRepo = taskRepo;
        this.timeLogRepo = timeLogRepo;
        this.subtaskRepo = subtaskRepo;
    }

    // ========================
    // Column operations
    // ========================

    public List<ColumnResponse> getAllColumns() {
        return columnRepo.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ColumnResponse createColumn(ColumnRequest request) {
        int maxOrder = columnRepo.findAll().stream()
                .mapToInt(c -> c.getDisplayOrder() != null ? c.getDisplayOrder() : 0)
                .max()
                .orElse(-1);

        BoardColumn col = new BoardColumn(
                request.getTitle(),
                request.getDisplayOrder() != null ? request.getDisplayOrder() : maxOrder + 1
        );
        col = columnRepo.save(col);
        return toResponse(col);
    }

    @Transactional
    public void deleteColumn(String columnId) {
        BoardColumn col = getColumnOrThrow(columnId);

        // Bottom-up delete: Subtask + TimeLog -> Task -> column. Every child FK is
        // non-nullable, so JPA refuses to unlink an owner while children still
        // reference it - the delete silently does nothing. Snapshot the collections first
        // because the FK update mutates them.
        for (Task task : new ArrayList<>(col.getTasks())) {
            for (Subtask subtask : new ArrayList<>(task.getSubtasks())) {
                task.getSubtasks().remove(subtask);
                subtaskRepo.deleteById(subtask.getId());
            }
            for (TimeLog log : new ArrayList<>(task.getTimeLogs())) {
                task.getTimeLogs().remove(log);
                timeLogRepo.deleteById(log.getId());
            }
            col.getTasks().remove(task);
            taskRepo.deleteById(task.getId());
        }
        columnRepo.deleteById(columnId);
    }

    @Transactional
    public ColumnResponse renameColumn(String columnId, String newTitle) {
        BoardColumn col = getColumnOrThrow(columnId);
        col.setTitle(newTitle);
        col = columnRepo.save(col);
        return toResponse(col);
    }

    // ========================
    // Task operations
    // ========================

    @Transactional
    public TaskResponse createTask(String columnId, TaskRequest request) {
        BoardColumn col = getColumnOrThrow(columnId);

        int maxOrder = col.getTasks().stream()
                .mapToInt(Task::getDisplayOrder)
                .max()
                .orElse(-1);

        Task task = toEntity(request, col,
                request.getDisplayOrder() != null ? request.getDisplayOrder() : maxOrder + 1);
        task = taskRepo.save(task);
        return toTaskResponse(task);
    }

    public TaskResponse getTask(String columnId, String taskId) {
        getColumnOrThrow(columnId);
        Optional<Task> opt = taskRepo.findById(taskId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Task not found: " + taskId);
        Task task = opt.get();
        if (!task.getBoardColumn().getId().equals(columnId)) {
            throw new IllegalArgumentException("Task does not belong to the specified column");
        }
        return toTaskResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(String columnId, String taskId, TaskRequest request) {
        getColumnOrThrow(columnId);
        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (!task.getBoardColumn().getId().equals(columnId)) {
            throw new IllegalArgumentException("Task does not belong to the specified column");
        }

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(parsePriority(request.getPriority()));
        task.setDeadline(request.getDeadline());
        task.setUrl(normalizeUrl(request.getUrl()));
        if (request.getDisplayOrder() != null) {
            task.setDisplayOrder(request.getDisplayOrder());
        }

        return toTaskResponse(taskRepo.save(task));
    }

    @Transactional
    public void deleteTask(String columnId, String taskId) {
        BoardColumn col = getColumnOrThrow(columnId);
        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if (!task.getBoardColumn().getId().equals(columnId)) {
            throw new IllegalArgumentException("Task does not belong to the specified column");
        }

        // Bottom-up delete, plus an explicit unlink: Task.subtasks / Task.timeLogs are eagerly
        // fetched, so the cached Task keeps referencing its children. Deleting only the rows
        // leaves those dangling references and makes taskRepo.deleteById() a silent no-op (204
        // with the task still in tasks). remove() has no orphanRemoval here, so it only unlinks
        // in memory - deleteById still does the actual DELETE.
        for (Subtask subtask : new ArrayList<>(task.getSubtasks())) {
            task.getSubtasks().remove(subtask);
            subtaskRepo.deleteById(subtask.getId());
        }
        for (TimeLog log : new ArrayList<>(task.getTimeLogs())) {
            task.getTimeLogs().remove(log);
            timeLogRepo.deleteById(log.getId());
        }
        // The column's EAGER tasks collection also still references this task, which blocks
        // its deletion just like the child collections above.
        col.getTasks().remove(task);
        taskRepo.deleteById(taskId);
    }

    @Transactional
    public TaskResponse moveTask(String sourceColumnId, String taskId, String targetColumnId) {
        BoardColumn tgtCol = getColumnOrThrow(targetColumnId);

        // Find the task directly (don't go through source column's tasks to avoid
        // orphanRemoval accidentally deleting it when we change the FK)
        Task taskToMove = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        // Set display order in target column (after current max)
        int maxOrder = tgtCol.getTasks().stream()
                .mapToInt(Task::getDisplayOrder)
                .max()
                .orElse(-1);
        taskToMove.setDisplayOrder(maxOrder + 1);

        // Change column — the FK update alone is sufficient.
        // Do NOT modify source column's task list (orphanRemoval would delete it).
        taskToMove.setBoardColumn(tgtCol);

        return toTaskResponse(taskRepo.save(taskToMove));
    }

    @Transactional
    public TaskResponse reorderTask(String columnId, String taskId, Integer newOrder) {
        BoardColumn col = getColumnOrThrow(columnId);
        List<Task> tasks = new ArrayList<>(col.getTasks());

        // Remove the task from its current position to avoid duplicate display orders
        Task target = null;
        for (Task t : tasks) {
            if (t.getId().equals(taskId)) {
                target = t;
                break;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        tasks.remove(target);

        // Insert at the new position, shift others down
        int insertIdx = Math.min(newOrder, tasks.size());
        tasks.add(insertIdx, target);

        // Update display order for all tasks (no orphanRemoval issue since we keep all)
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setDisplayOrder(i);
        }
        taskRepo.saveAll(tasks);

        return toTaskResponse(target);
    }

    // ========================
    // Time tracking
    // ========================

    private Task getTaskOrThrow(String columnId, String taskId) {
        getColumnOrThrow(columnId);
        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if (!task.getBoardColumn().getId().equals(columnId)) {
            throw new IllegalArgumentException("Task does not belong to the specified column");
        }
        return task;
    }

    @Transactional
    public TimeLogResponse startTime(String columnId, String taskId, TimeLogRequest request) {
        Task task = getTaskOrThrow(columnId, taskId);

        boolean running = task.getTimeLogs().stream().anyMatch(log -> log.getStoppedAt() == null);
        if (running) {
            throw new IllegalArgumentException("Time log already running for this task — stop it first");
        }

        TimeLog log = new TimeLog();
        log.setTask(task);
        log.setStartedAt(nowToSeconds());
        log.setDescription(trimOrNull(request == null ? null : request.getDescription()));
        log = timeLogRepo.save(log);
        taskRepo.save(task);
        return toTimeLogResponse(log);
    }

    @Transactional
    public TimeLogResponse stopTime(String columnId, String taskId, TimeLogRequest request) {
        Task task = getTaskOrThrow(columnId, taskId);

        TimeLog open = task.getTimeLogs().stream()
                .filter(log -> log.getStoppedAt() == null)
                .max(java.util.Comparator.comparing(TimeLog::getStartedAt))
                .orElseThrow(() -> new IllegalArgumentException("No running time log for this task"));

        open.setStoppedAt(nowToSeconds());

        // The STOP prompt shows the description that was typed at START, so the frontend
        // re-submits the same text. Only append genuinely new text.
        String extra = trimOrNull(request == null ? null : request.getDescription());
        String existing = trimOrNull(open.getDescription());
        if (extra != null && !extra.equals(existing)) {
            open.setDescription(existing == null ? extra : existing + "\n" + extra);
        }

        open = timeLogRepo.save(open);
        taskRepo.save(task);
        return toTimeLogResponse(open);
    }

    public List<TimeLogResponse> getTimeLogs(String columnId, String taskId) {
        Task task = getTaskOrThrow(columnId, taskId);
        return task.getTimeLogs().stream()
                .sorted(java.util.Comparator.comparing(TimeLog::getStartedAt).reversed())
                .map(this::toTimeLogResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TimeSummaryResponse getTimeSummary(Integer requestedDays) {
        int days = requestedDays == null || requestedDays <= 0 ? 7 : Math.min(requestedDays, 365);
        LocalDateTime to = nowToSeconds();
        return buildSummary(to.minusDays(days), to);
    }

    // Explicit calendar range: both ends are whole days, inclusive.
    @Transactional(readOnly = true)
    public TimeSummaryResponse getTimeSummary(java.time.LocalDate fromDay, java.time.LocalDate toDay) {
        LocalDateTime from = fromDay.atStartOfDay();
        LocalDateTime to = toDay.plusDays(1).atStartOfDay();
        if (to.compareTo(from) <= 0) {
            to = from.plusDays(1);
        }
        if (from.compareTo(to.minusDays(365)) < 0) {
            from = to.minusDays(365);
        }
        return buildSummary(from, to);
    }

    // Number of calendar days touched by the half-open window [from, to).
    private int daySpan(LocalDateTime from, LocalDateTime to) {
        java.time.LocalDate first = from.toLocalDate();
        java.time.LocalDate last = to.minusNanos(1).toLocalDate();
        int span = 1;
        for (java.time.LocalDate cur = first; cur.isBefore(last); cur = cur.plusDays(1)) {
            span++;
        }
        return span;
    }

    private TimeSummaryResponse buildSummary(LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = nowToSeconds();
        java.util.Map<java.time.LocalDate, java.util.Map<String, TaskDaySummary>> dayTasks = new java.util.LinkedHashMap<>();

        java.util.List<TaskTimeSummary> summaries = new java.util.ArrayList<>();
        long grandTotal = 0;
        int totalLogs = 0;

        for (BoardColumn col : columnRepo.findAll()) {
            for (Task task : col.getTasks()) {
                long total = 0;
                int logs = 0;
                int open = 0;
                java.util.LinkedHashSet<String> comments = new java.util.LinkedHashSet<>();
                java.util.List<TimeLogResponse> entries = new java.util.ArrayList<>();

                for (TimeLog log : task.getTimeLogs()) {
                    boolean running = log.getStoppedAt() == null;
                    LocalDateTime start = log.getStartedAt();
                    LocalDateTime end = running ? now : log.getStoppedAt();
                    if (end.compareTo(from) < 0) continue;          // finished before the window
                    LocalDateTime winStart = start.compareTo(from) < 0 ? from : start;
                    LocalDateTime winEnd = end.compareTo(to) > 0 ? to : end;
                    if (winEnd.compareTo(winStart) <= 0) continue;
                    if (running) open++;

                    String comment = log.getDescription();
                    if (comment != null && !comment.isBlank()) {
                        comments.add(comment.trim());
                    }

                    entries.add(toTimeLogResponse(log));
                    total += Math.max(0, Duration.between(winStart, winEnd).toSeconds());
                    logs++;

                    java.time.LocalDate first = winStart.toLocalDate();
                    java.time.LocalDate last = winEnd.minusNanos(1).toLocalDate();
                    for (java.time.LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                        LocalDateTime dayFrom = day.atStartOfDay();
                        LocalDateTime dayTo = day.plusDays(1).atStartOfDay();
                        LocalDateTime partStart = winStart.compareTo(dayFrom) < 0 ? dayFrom : winStart;
                        LocalDateTime partEnd = winEnd.compareTo(dayTo) > 0 ? dayTo : winEnd;
                        long secs = Math.max(0, Duration.between(partStart, partEnd).toSeconds());

                        java.util.Map<String, TaskDaySummary> tasks = dayTasks.computeIfAbsent(day, k -> new java.util.LinkedHashMap<>());
                        TaskDaySummary dayTask = tasks.computeIfAbsent(task.getId(), k -> new TaskDaySummary(task.getId(), task.getTitle(), col.getTitle()));
                        dayTask.setTotalSeconds(dayTask.getTotalSeconds() + secs);

                        TimeLogResponse part = toTimeLogResponse(log);
                        part.setStartedAt(partStart);
                        part.setStoppedAt(running ? null : partEnd);
                        part.setRunning(running);
                        part.setDurationSeconds(secs);
                        dayTask.addToLogs(part);
                    }
                }

                if (logs == 0) continue;
                totalLogs += logs;
                grandTotal += total;

                TaskTimeSummary s = new TaskTimeSummary();
                s.setTaskId(task.getId());
                s.setTitle(task.getTitle());
                s.setColumnId(col.getId());
                s.setColumnTitle(col.getTitle());
                s.setLogCount(logs);
                s.setOpenLogs(open);
                s.setTotalSeconds(total);
                entries.sort(java.util.Comparator.comparing(TimeLogResponse::getStartedAt).reversed());
                s.setDescriptions(comments.stream().limit(6).toList());
                s.setLogs(entries.size() > 40 ? new java.util.ArrayList<>(entries.subList(0, 40)) : entries);
                summaries.add(s);
            }
        }

        summaries.sort(java.util.Comparator.comparing(TaskTimeSummary::getTotalSeconds).reversed());

        java.util.List<java.time.LocalDate> dayOrder = new java.util.ArrayList<>(dayTasks.keySet());
        dayOrder.sort(java.util.Comparator.comparing((java.time.LocalDate a) -> a));

        java.util.List<DaySummary> byDay = new java.util.ArrayList<>();
        for (java.time.LocalDate day : dayOrder) {
            java.util.List<TaskDaySummary> dayTaskList = new java.util.ArrayList<>(dayTasks.get(day).values());
            dayTaskList.sort(java.util.Comparator.comparing((TaskDaySummary t) -> -t.getTotalSeconds()));
            long dayTotal = 0;
            int dayLogs = 0;
            for (TaskDaySummary t : dayTaskList) {
                dayTotal += t.getTotalSeconds();
                dayLogs += t.getLogs().size();
            }
            DaySummary ds = new DaySummary();
            ds.setDate(day);
            ds.setTotalSeconds(dayTotal);
            ds.setLogCount(dayLogs);
            ds.setTasks(dayTaskList);
            byDay.add(ds);
        }

        TimeSummaryResponse resp = new TimeSummaryResponse();
        resp.setDays(daySpan(from, to));
        resp.setFrom(from);
        resp.setTo(to);
        resp.setTotalSeconds(grandTotal);
        resp.setLogCount(totalLogs);
        resp.setActiveTaskCount(summaries.size());
        resp.setTasks(summaries);
        resp.setByDay(byDay);
        return resp;
    }

    // ========================
    // Subtask checklist (one level deep - a Subtask owns nothing)
    // ========================

    private Subtask getSubtaskOrThrow(String columnId, String taskId, String subtaskId) {
        Task task = getTaskOrThrow(columnId, taskId);
        Subtask subtask = subtaskRepo.findById(subtaskId)
                .orElseThrow(() -> new IllegalArgumentException("Subtask not found: " + subtaskId));
        if (subtask.getOwnerTask() == null || !subtask.getOwnerTask().getId().equals(taskId)) {
            throw new IllegalArgumentException("Subtask does not belong to the specified task");
        }
        return subtask;
    }

    @Transactional
    public SubtaskResponse createSubtask(String columnId, String taskId, SubtaskRequest request) {
        Task task = getTaskOrThrow(columnId, taskId);
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Subtask title is required");
        }

        int maxOrder = task.getSubtasks().stream()
                .mapToInt(s -> s.getDisplayOrder() != null ? s.getDisplayOrder() : 0)
                .max()
                .orElse(-1);

        Subtask subtask = new Subtask(request.getTitle().trim(), maxOrder + 1);
        subtask.setOwnerTask(task);
        subtask = subtaskRepo.save(subtask);
        taskRepo.save(task);
        return toSubtaskResponse(subtask);
    }

    public List<SubtaskResponse> listSubtasks(String columnId, String taskId) {
        Task task = getTaskOrThrow(columnId, taskId);
        return task.getSubtasks().stream()
                .sorted(java.util.Comparator.comparing(Subtask::getDisplayOrder))
                .map(this::toSubtaskResponse)
                .toList();
    }

    // Rename a row. The title is required (@NotBlank on SubtaskRequest) so this endpoint
    // reuses the same DTO; the checkbox state is left alone.
    @Transactional
    public SubtaskResponse renameSubtask(String columnId, String taskId, String subtaskId, SubtaskRequest request) {
        Subtask subtask = getSubtaskOrThrow(columnId, taskId, subtaskId);
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Subtask title is required");
        }
        subtask.setTitle(request.getTitle().trim());
        subtask = subtaskRepo.save(subtask);
        taskRepo.save(subtask.getOwnerTask());
        return toSubtaskResponse(subtask);
    }

    // Set / clear the checkbox. `done` is null -> flip whatever the state currently is.
    @Transactional
    public SubtaskResponse toggleSubtask(String columnId, String taskId, String subtaskId, String done) {
        Subtask subtask = getSubtaskOrThrow(columnId, taskId, subtaskId);
        boolean target = done == null || done.isBlank()
                ? !subtask.getDone()
                : switch (done.trim().toLowerCase()) {
                    case "true", "1", "yes", "done" -> true;
                    case "false", "0", "no", "undone" -> false;
                    default -> throw new IllegalArgumentException("Unknown done value: " + done);
                };
        subtask.setDone(target);
        subtask = subtaskRepo.save(subtask);
        taskRepo.save(subtask.getOwnerTask());
        return toSubtaskResponse(subtask);
    }

    @Transactional
    public void deleteSubtask(String columnId, String taskId, String subtaskId) {
        Subtask subtask = getSubtaskOrThrow(columnId, taskId, subtaskId);
        Task task = subtask.getOwnerTask();
        // Unlink the row from the parent's EAGER collection BEFORE deleting it: Task.subtasks
        // is eagerly fetched, so saving the parent while it still references the deleted child
        // re-persists that child and puts the row back (same silent-no-op family of bugs as
        // delete() across a non-nullable FK). There is no orphanRemoval here, so remove() only
        // unlinks in memory - subtaskRepo.deleteById() does the actual DELETE.
        task.getSubtasks().remove(subtask);
        subtaskRepo.deleteById(subtask.getId());
        taskRepo.save(task);
    }

    // ========================
    // Mapping helpers
    // ========================

    private BoardColumn getColumnOrThrow(String columnId) {
        return columnRepo.findById(columnId)
                .orElseThrow(() -> new IllegalArgumentException("Column not found: " + columnId));
    }

    private Task toEntity(TaskRequest request, BoardColumn col, int displayOrder) {
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(parsePriority(request.getPriority()));
        task.setDeadline(request.getDeadline());
        task.setUrl(normalizeUrl(request.getUrl()));
        task.setBoardColumn(col);
        task.setDisplayOrder(displayOrder);
        return task;
    }

    private ColumnResponse toResponse(BoardColumn col) {
        ColumnResponse resp = new ColumnResponse();
        resp.setId(col.getId());
        resp.setTitle(col.getTitle());
        resp.setDisplayOrder(col.getDisplayOrder());
        resp.setTaskCount(col.getTasks().size());
        resp.setCreatedAt(col.getCreatedAt());
        resp.setUpdatedAt(col.getUpdatedAt());

        List<TaskResponse> taskResponses = col.getTasks().stream()
                .sorted(Comparator.comparing(Task::getDisplayOrder))
                .map(this::toTaskResponse)
                .toList();
        resp.setTasks(taskResponses);
        return resp;
    }

    private TaskResponse toTaskResponse(Task task) {
        TaskResponse resp = new TaskResponse();
        resp.setId(task.getId());
        resp.setTitle(task.getTitle());
        resp.setDescription(task.getDescription());
        resp.setPriority(task.getPriority().name().toLowerCase());
        resp.setDeadline(task.getDeadline());
        resp.setUrl(task.getUrl());
        resp.setDisplayOrder(task.getDisplayOrder());
        resp.setColumnId(task.getBoardColumn() != null ? task.getBoardColumn().getId() : null);
        resp.setCreatedAt(task.getCreatedAt());
        resp.setUpdatedAt(task.getUpdatedAt());
        resp.setRunning(task.isRunning());
        resp.setTimeLogged(task.getTimeLogs().stream()
                .mapToLong(TimeLog::getDurationSeconds)
                .sum());
        resp.setTimeLogs(task.getTimeLogs().stream()
                .sorted(java.util.Comparator.comparing(TimeLog::getStartedAt).reversed())
                .map(this::toTimeLogResponse)
                .toList());
        resp.setSubtasks(task.getSubtasks().stream()
                .sorted(java.util.Comparator.comparing(Subtask::getDisplayOrder))
                .map(this::toSubtaskResponse)
                .toList());
        resp.setSubtaskCount(task.subtaskCount());
        resp.setSubtaskDoneCount(task.doneSubtaskCount());
        return resp;
    }

    private SubtaskResponse toSubtaskResponse(Subtask subtask) {
        SubtaskResponse resp = new SubtaskResponse();
        resp.setId(subtask.getId());
        resp.setTaskId(subtask.getOwnerTask() != null ? subtask.getOwnerTask().getId() : null);
        resp.setTitle(subtask.getTitle());
        resp.setDone(subtask.getDone());
        resp.setDisplayOrder(subtask.getDisplayOrder());
        resp.setCreatedAt(subtask.getCreatedAt());
        resp.setUpdatedAt(subtask.getUpdatedAt());
        return resp;
    }

    private TimeLogResponse toTimeLogResponse(TimeLog log) {
        TimeLogResponse resp = new TimeLogResponse();
        resp.setId(log.getId());
        resp.setTaskId(log.getTask() != null ? log.getTask().getId() : null);
        resp.setStartedAt(log.getStartedAt());
        resp.setStoppedAt(log.getStoppedAt());
        resp.setRunning(log.getStoppedAt() == null);
        resp.setDescription(log.getDescription());
        resp.setDurationSeconds(log.getDurationSeconds());
        return resp;
    }

    private String normalizeUrl(String url) {
        String trimmed = trimOrNull(url);
        if (trimmed == null) return null;
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        return "https://" + trimmed;
    }

    // Timestamps are stored with whole-second precision: no fractional part in the DB,
    // so a derived duration is always an exact number of seconds (never millis).
    private LocalDateTime nowToSeconds() {
        return LocalDateTime.now().withNano(0);
    }

    private Priority parsePriority(String value) {
        String trimmed = trimOrNull(value);
        if (trimmed == null) return Priority.MEDIUM;
        String upper = trimmed.toUpperCase();
        for (Priority candidate : Priority.values()) {
            if (candidate.name().equals(upper)) return candidate;
        }
        throw new IllegalArgumentException("Unknown priority: " + upper);
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
