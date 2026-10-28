package com.kanban.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "time_logs")
public class TimeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // FK to the task this log belongs to. Named `task` so that Task's
    // @OneToMany(mappedBy = "task") maps to this attribute.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    // null => the log is still running (task clock is running)
    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    @Column(columnDefinition = "TEXT")
    private String description;

    public TimeLog() {}

    // ---- Getters / Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Task getTask() { return task; }
    public void setTask(Task task) { this.task = task; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(LocalDateTime stoppedAt) { this.stoppedAt = stoppedAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isRunning() { return stoppedAt == null; }

    // Seconds spent; for a running log, seconds so far.
    public long getDurationSeconds() {
        LocalDateTime end = stoppedAt != null ? stoppedAt : LocalDateTime.now();
        return Math.max(0, Duration.between(startedAt, end).toSeconds());
    }
}
