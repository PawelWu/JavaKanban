package com.kanban.controller;

import com.kanban.dto.ColumnRequest;
import com.kanban.dto.ColumnResponse;
import com.kanban.dto.SubtaskRequest;
import com.kanban.dto.SubtaskResponse;
import com.kanban.dto.TaskRequest;
import com.kanban.dto.TaskResponse;
import com.kanban.dto.TimeLogRequest;
import com.kanban.dto.TimeLogResponse;
import com.kanban.dto.TimeSummaryResponse;
import com.kanban.service.KanbanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class KanbanController {

    private final KanbanService kanbanService;

    public KanbanController(KanbanService kanbanService) {
        this.kanbanService = kanbanService;
    }

    // ========================
    // Column endpoints
    // ========================

    @GetMapping("/columns")
    public ResponseEntity<?> getAllColumns() {
        return ResponseEntity.ok(kanbanService.getAllColumns());
    }

    @PostMapping("/columns")
    public ResponseEntity<ColumnResponse> createColumn(@Valid @RequestBody ColumnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kanbanService.createColumn(request));
    }

    @DeleteMapping("/columns/{columnId}")
    public ResponseEntity<Void> deleteColumn(@PathVariable String columnId) {
        kanbanService.deleteColumn(columnId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/columns/{columnId}")
    public ResponseEntity<ColumnResponse> renameColumn(
            @PathVariable String columnId,
            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }
        return ResponseEntity.ok(kanbanService.renameColumn(columnId, title.trim()));
    }

    // ========================
    // Task endpoints
    // ========================

    @PostMapping("/columns/{columnId}/tasks")
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable String columnId,
            @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kanbanService.createTask(columnId, request));
    }

    @GetMapping("/columns/{columnId}/tasks/{taskId}")
    public ResponseEntity<TaskResponse> getTask(
            @PathVariable String columnId,
            @PathVariable String taskId) {
        return ResponseEntity.ok(kanbanService.getTask(columnId, taskId));
    }

    @PutMapping("/columns/{columnId}/tasks/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable String columnId,
            @PathVariable String taskId,
            @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(kanbanService.updateTask(columnId, taskId, request));
    }

    @DeleteMapping("/columns/{columnId}/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable String columnId,
            @PathVariable String taskId) {
        kanbanService.deleteTask(columnId, taskId);
        return ResponseEntity.noContent().build();
    }

    // ========================
    // Time tracking
    // ========================

    @PostMapping("/columns/{columnId}/tasks/{taskId}/time/start")
    public ResponseEntity<TimeLogResponse> startTime(
            @PathVariable String columnId,
            @PathVariable String taskId,
            @Valid @RequestBody TimeLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kanbanService.startTime(columnId, taskId, request));
    }

    @PostMapping("/columns/{columnId}/tasks/{taskId}/time/stop")
    public ResponseEntity<TimeLogResponse> stopTime(
            @PathVariable String columnId,
            @PathVariable String taskId,
            @Valid @RequestBody TimeLogRequest request) {
        return ResponseEntity.ok(kanbanService.stopTime(columnId, taskId, request));
    }

    @GetMapping("/columns/{columnId}/tasks/{taskId}/time")
    public ResponseEntity<?> getTimeLogs(
            @PathVariable String columnId,
            @PathVariable String taskId) {
        return ResponseEntity.ok(kanbanService.getTimeLogs(columnId, taskId));
    }

    // ========================
    // Subtask checklist (one level below a task)
    // ========================

    @PostMapping("/columns/{columnId}/tasks/{taskId}/subtasks")
    public ResponseEntity<SubtaskResponse> createSubtask(
            @PathVariable String columnId,
            @PathVariable String taskId,
            @Valid @RequestBody SubtaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kanbanService.createSubtask(columnId, taskId, request));
    }

    @GetMapping("/columns/{columnId}/tasks/{taskId}/subtasks")
    public ResponseEntity<?> listSubtasks(
            @PathVariable String columnId,
            @PathVariable String taskId) {
        return ResponseEntity.ok(kanbanService.listSubtasks(columnId, taskId));
    }

    @PutMapping("/columns/{columnId}/tasks/{taskId}/subtasks/{subtaskId}")
    public ResponseEntity<SubtaskResponse> renameSubtask(
            @PathVariable String columnId,
            @PathVariable String taskId,
            @PathVariable String subtaskId,
            @Valid @RequestBody SubtaskRequest request) {
        return ResponseEntity.ok(kanbanService.renameSubtask(columnId, taskId, subtaskId, request));
    }

    // Check / uncheck a row. Body: {"done":"true"} or {"done":"false"}; an empty body
    // flips the current state. Map<String,String> (not SubtaskRequest) because @NotBlank
    // on the DTO's title would reject a checkbox-only payload.
    @PatchMapping("/columns/{columnId}/tasks/{taskId}/subtasks/{subtaskId}/toggle")
    public ResponseEntity<SubtaskResponse> toggleSubtask(
            @PathVariable String columnId,
            @PathVariable String taskId,
            @PathVariable String subtaskId,
            Map<String, String> body) {
        String done = body == null ? null : body.get("done");
        return ResponseEntity.ok(kanbanService.toggleSubtask(columnId, taskId, subtaskId, done));
    }

    @DeleteMapping("/columns/{columnId}/tasks/{taskId}/subtasks/{subtaskId}")
    public ResponseEntity<Void> deleteSubtask(
            @PathVariable String columnId,
            @PathVariable String taskId,
            @PathVariable String subtaskId) {
        kanbanService.deleteSubtask(columnId, taskId, subtaskId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/time/summary")
    public ResponseEntity<TimeSummaryResponse> getTimeSummary() {
        return ResponseEntity.ok(kanbanService.getTimeSummary(null));
    }

    @GetMapping("/time/summary/{days}")
    public ResponseEntity<TimeSummaryResponse> getTimeSummary(
            @PathVariable Integer days) {
        return ResponseEntity.ok(kanbanService.getTimeSummary(days));
    }

    // Calendar range, both dates inclusive: /api/time/summary/2026-10-16/2026-10-23
    @GetMapping("/time/summary/{fromDate}/{toDate}")
    public ResponseEntity<TimeSummaryResponse> getTimeSummary(
            @PathVariable java.time.LocalDate fromDate,
            @PathVariable java.time.LocalDate toDate) {
        return ResponseEntity.ok(kanbanService.getTimeSummary(fromDate, toDate));
    }

    // ========================
    // Move task between columns
    // ========================

    @PatchMapping("/columns/{sourceColumnId}/move")
    public ResponseEntity<TaskResponse> moveTask(
            @PathVariable String sourceColumnId,
            @RequestBody Map<String, String> body) {
        String taskId = body.get("taskId");
        String targetColumnId = body.get("targetColumnId");

        if (taskId == null || targetColumnId == null) {
            return ResponseEntity.badRequest().body(null);
        }

        return ResponseEntity.ok(kanbanService.moveTask(sourceColumnId, taskId, targetColumnId));
    }

    // ========================
    // Reorder task within column
    // ========================

    @PatchMapping("/columns/{columnId}/tasks/{taskId}/reorder")
    public ResponseEntity<TaskResponse> reorderTask(
            @PathVariable String columnId,
            @PathVariable String taskId,
            @RequestBody Map<String, Integer> body) {
        Integer newOrder = body.get("displayOrder");
        if (newOrder == null) {
            return ResponseEntity.badRequest().body(null);
        }

        return ResponseEntity.ok(kanbanService.reorderTask(columnId, taskId, newOrder));
    }
}
