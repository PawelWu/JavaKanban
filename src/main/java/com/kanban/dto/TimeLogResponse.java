package com.kanban.dto;

import java.time.LocalDateTime;

public class TimeLogResponse {

    private String id;
    private String taskId;
    private LocalDateTime startedAt;
    private LocalDateTime stoppedAt;
    private boolean running;
    private String description;
    private long durationSeconds;

    public TimeLogResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(LocalDateTime stoppedAt) { this.stoppedAt = stoppedAt; }

    public boolean getRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
}
