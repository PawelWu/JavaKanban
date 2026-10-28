package com.kanban.dto;

import java.util.List;

import com.kanban.dto.TimeLogResponse;

public class TaskTimeSummary {

    private String taskId;
    private String title;
    private String columnId;
    private String columnTitle;
    private int logCount;
    private int openLogs;
    private long totalSeconds;

    // Distinct log comments inside the requested period - lets the summary page
    // show a readable sentence instead of only counters.
    private List<String> descriptions;
    private List<TimeLogResponse> logs;

    public TaskTimeSummary() {}

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getColumnId() { return columnId; }
    public void setColumnId(String columnId) { this.columnId = columnId; }

    public String getColumnTitle() { return columnTitle; }
    public void setColumnTitle(String columnTitle) { this.columnTitle = columnTitle; }

    public int getLogCount() { return logCount; }
    public void setLogCount(int logCount) { this.logCount = logCount; }

    public int getOpenLogs() { return openLogs; }
    public void setOpenLogs(int openLogs) { this.openLogs = openLogs; }

    public long getTotalSeconds() { return totalSeconds; }
    public void setTotalSeconds(long totalSeconds) { this.totalSeconds = totalSeconds; }

    public List<String> getDescriptions() { return descriptions; }
    public void setDescriptions(List<String> descriptions) { this.descriptions = descriptions; }

    // The individual sessions inside the period, newest first: lets the summary page
    // list the times and notes, not just totals per task.
    public List<TimeLogResponse> getLogs() { return logs; }
    public void setLogs(List<TimeLogResponse> logs) { this.logs = logs; }
}
