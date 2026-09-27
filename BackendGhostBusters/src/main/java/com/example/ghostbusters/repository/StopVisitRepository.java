package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.StopVisit;
import com.example.ghostbusters.entity.StopVisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface StopVisitRepository extends JpaRepository<StopVisit, Long> {

    long countByStatus(StopVisitStatus status);

    long countByStatusAndCheckedAtAfter(StopVisitStatus status, LocalDateTime after);

    List<StopVisit> findByCheckedAtAfter(LocalDateTime after);
}