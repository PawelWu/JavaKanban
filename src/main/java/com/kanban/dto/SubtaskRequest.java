package com.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SubtaskRequest {

    @NotBlank(message = "Subtask title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    // Optional checkbox state: null = leave unchanged (used by the toggle endpoint).
    private boolean done;

    public SubtaskRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public boolean getDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
}
