package com.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class TaskRequest {

    @NotBlank(message = "Task title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    private String description;
    private String priority = "MEDIUM";
    private LocalDate deadline;

    @Size(max = 500, message = "URL must be at most 500 characters")
    private String url;
    private Integer displayOrder;

    public TaskRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
