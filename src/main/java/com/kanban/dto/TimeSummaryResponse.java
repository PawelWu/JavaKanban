package com.kanban.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.kanban.dto.DaySummary;

public class TimeSummaryResponse {

    private int days;
    private LocalDateTime from;
    private LocalDateTime to;
    private long totalSeconds;
    private int logCount;
    private int activeTaskCount;
    private java.util.List<TaskTimeSummary> tasks;
    private java.util.List<DaySummary> byDay;

    public TimeSummaryResponse() {}

    public int getDays() { return days; }
    public void setDays(int days) { this.days = days; }

    public LocalDateTime getFrom() { return from; }
    public void setFrom(LocalDateTime from) { this.from = from; }

    public LocalDateTime getTo() { return to; }
    public void setTo(LocalDateTime to) { this.to = to; }

    public long getTotalSeconds() { return totalSeconds; }
    public void setTotalSeconds(long totalSeconds) { this.totalSeconds = totalSeconds; }

    public int getLogCount() { return logCount; }
    public void setLogCount(int logCount) { this.logCount = logCount; }

    public int getActiveTaskCount() { return activeTaskCount; }
    public void setActiveTaskCount(int activeTaskCount) { this.activeTaskCount = activeTaskCount; }

    public java.util.List<TaskTimeSummary> getTasks() { return tasks; }
    public void setTasks(java.util.List<TaskTimeSummary> tasks) { this.tasks = tasks; }

    public java.util.List<DaySummary> getByDay() { return byDay; }
    public void setByDay(java.util.List<DaySummary> byDay) { this.byDay = byDay; }
}
