package com.kanban.dto;

import jakarta.validation.constraints.Size;

public class TimeLogRequest {

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    public TimeLogRequest() {}

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
