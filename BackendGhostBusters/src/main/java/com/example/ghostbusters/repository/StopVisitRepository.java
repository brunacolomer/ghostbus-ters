package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.StopVisit;
import com.example.ghostbusters.entity.StopVisitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface StopVisitRepository extends JpaRepository<StopVisit, Long> {

    @Query("select min(v.checkedAt) from StopVisit v where v.status = :status")
    LocalDateTime findFirstCheckedAtByStatus(StopVisitStatus status);

    long countByStatus(StopVisitStatus status);

    long countByStatusAndCheckedAtAfter(StopVisitStatus status, LocalDateTime after);

    List<StopVisit> findByCheckedAtAfter(LocalDateTime after);

    @Query("select distinct concat(v.tripId, '|', v.stopId) from StopVisit v where v.checkedAt > :after")
    Set<String> findCheckedStopKeysAfter(LocalDateTime after);
}
