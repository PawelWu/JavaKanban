package com.kanban.dto;

import java.util.List;

import com.kanban.dto.TimeLogResponse;

public class TaskDaySummary {

    private String taskId;
    private String title;
    private String columnTitle;
    private long totalSeconds;
    private java.util.List<TimeLogResponse> logs;

    public TaskDaySummary() {}

    public TaskDaySummary(String taskId, String title, String columnTitle) {
        this.taskId = taskId;
        this.title = title;
        this.columnTitle = columnTitle;
        this.logs = new java.util.ArrayList<>();
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getColumnTitle() { return columnTitle; }
    public void setColumnTitle(String columnTitle) { this.columnTitle = columnTitle; }

    public long getTotalSeconds() { return totalSeconds; }
    public void setTotalSeconds(long totalSeconds) { this.totalSeconds = totalSeconds; }

    public java.util.List<TimeLogResponse> getLogs() { return logs; }
    public void setLogs(java.util.List<TimeLogResponse> logs) { this.logs = logs; }

    // Used while building the summary (one session can be split over several days).
    public void addToLogs(TimeLogResponse log) { logs.add(log); }
}
