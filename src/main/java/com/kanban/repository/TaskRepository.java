package com.kanban.repository;

import com.kanban.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    List<Task> findAllByBoardColumnIdOrderByDisplayOrderAsc(String boardColumnId);

    long countByBoardColumnId(String boardColumnId);
}
