package com.kanban.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "columns")
public class BoardColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    // CascadeType.PERSIST only (no orphanRemoval). KanbanService.deleteColumn() has to
    // delete the tasks (and their time logs) explicitly before deleting the column,
    // because Task.boardColumn is a non-nullable FK (optional = false).
    @OneToMany(mappedBy = "boardColumn", cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    private List<Task> tasks = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public BoardColumn() {}

    public BoardColumn(String title, Integer displayOrder) {
        this.title = title;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ---- Getters / Setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) {
        // Do NOT null out boardColumn — tasks may be moved to another column,
        // not deleted. The FK on Task handles the relationship.
        this.tasks.clear();
        this.tasks.addAll(tasks);
        // Ensure bidirectional link is correct for new tasks
        tasks.forEach(t -> t.setBoardColumn(this));
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
