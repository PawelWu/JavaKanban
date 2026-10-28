package com.kanban.dto;

import java.util.List;

import com.kanban.dto.TaskDaySummary;

public class DaySummary {

    private java.time.LocalDate date;
    private long totalSeconds;
    private int logCount;
    private java.util.List<TaskDaySummary> tasks;

    public DaySummary() {}

    public java.time.LocalDate getDate() { return date; }
    public void setDate(java.time.LocalDate date) { this.date = date; }

    public long getTotalSeconds() { return totalSeconds; }
    public void setTotalSeconds(long totalSeconds) { this.totalSeconds = totalSeconds; }

    public int getLogCount() { return logCount; }
    public void setLogCount(int logCount) { this.logCount = logCount; }

    public java.util.List<TaskDaySummary> getTasks() { return tasks; }
    public void setTasks(java.util.List<TaskDaySummary> tasks) { this.tasks = tasks; }
}
