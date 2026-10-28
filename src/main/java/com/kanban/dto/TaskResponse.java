package com.kanban.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TaskResponse {

    private String id;
    private String title;
    private String description;
    private String priority;
    private LocalDate deadline;
    private String url;
    private Integer displayOrder;
    private String columnId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean running;
    private long timeLogged;
    private java.util.List<TimeLogResponse> timeLogs;
    private java.util.List<SubtaskResponse> subtasks;
    private int subtaskCount;
    private int subtaskDoneCount;

    public TaskResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getColumnId() { return columnId; }
    public void setColumnId(String columnId) { this.columnId = columnId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean getRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }

    public long getTimeLogged() { return timeLogged; }
    public void setTimeLogged(long timeLogged) { this.timeLogged = timeLogged; }

    public java.util.List<TimeLogResponse> getTimeLogs() { return timeLogs; }
    public void setTimeLogs(java.util.List<TimeLogResponse> timeLogs) { this.timeLogs = timeLogs; }

    public java.util.List<SubtaskResponse> getSubtasks() { return subtasks; }
    public void setSubtasks(java.util.List<SubtaskResponse> subtasks) { this.subtasks = subtasks; }

    public int getSubtaskCount() { return subtaskCount; }
    public void setSubtaskCount(int subtaskCount) { this.subtaskCount = subtaskCount; }

    public int getSubtaskDoneCount() { return subtaskDoneCount; }
    public void setSubtaskDoneCount(int subtaskDoneCount) { this.subtaskDoneCount = subtaskDoneCount; }
}
