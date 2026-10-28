package com.kanban.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ColumnRequest {

    @NotBlank(message = "Column title is required")
    @Size(max = 100, message = "Title must be at most 100 characters")
    private String title;

    private Integer displayOrder;

    public ColumnRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
