package com.kanban.repository;

import com.kanban.entity.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ColumnRepository extends JpaRepository<BoardColumn, String> {

    List<BoardColumn> findAllByOrderByDisplayOrderAsc();
}
