package com.kanban.repository;

import com.kanban.entity.TimeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TimeLogRepository extends JpaRepository<TimeLog, String> {
}
