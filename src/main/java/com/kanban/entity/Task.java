package com.kanban.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    private LocalDate deadline;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    // Optional link (clickable from the board)
    @Column(name = "url", length = 500)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "column_id", nullable = false)
    private BoardColumn boardColumn;

    // Time tracking logs. CascadeType.PERSIST only - NO orphanRemoval (that would drop
    // logs during drag-and-drop reloads) and there is no DELETE cascade in jakarta's
    // CascadeType enum: KanbanService.deleteTask() must delete the logs explicitly first,
    // since TimeLog.task is a non-nullable FK (optional = false).
    @OneToMany(mappedBy = "task", cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @OrderBy("startedAt ASC")
    private List<TimeLog> timeLogs = new ArrayList<>();

    // Subtask checklist. One level deep: Subtask has no children of its own, so a
    // task can only own plain checklist rows (no recursive trees to untangle).
    // Same cascade rule as the logs above: CascadeType.PERSIST only, NO orphanRemoval
    // (mutating this list must never delete rows). KanbanService.deleteTask() deletes
    // subtasks explicitly before deleting the task - the FK on Subtask is non-nullable.
    @OneToMany(mappedBy = "ownerTask", cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    private List<Subtask> subtasks = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Task() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ---- Getters / Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public BoardColumn getBoardColumn() { return boardColumn; }
    public void setBoardColumn(BoardColumn boardColumn) { this.boardColumn = boardColumn; }

    public List<TimeLog> getTimeLogs() { return timeLogs; }

    public List<Subtask> getSubtasks() { return subtasks; }
    public void setSubtasks(List<Subtask> subtasks) {
        this.subtasks.clear();
        this.subtasks.addAll(subtasks);
        subtasks.forEach(s -> s.setOwnerTask(this));
    }

    public int subtaskCount() { return subtasks == null ? 0 : subtasks.size(); }

    public int doneSubtaskCount() {
        if (subtasks == null) return 0;
        int done = 0;
        for (Subtask s : subtasks) {
            if (s.getDone()) done++;
        }
        return done;
    }

    public boolean isRunning() {
        return timeLogs != null && timeLogs.stream().anyMatch(l -> l.getStoppedAt() == null);
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
