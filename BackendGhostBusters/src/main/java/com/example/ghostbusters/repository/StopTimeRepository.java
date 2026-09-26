package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.StopTime;
import com.example.ghostbusters.entity.StopTimeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StopTimeRepository extends JpaRepository<StopTime, StopTimeId> {
    List<StopTime> findByTripIdOrderByStopSequenceAsc(String tripId);
    List<StopTime> findByTripIdInOrderByStopSequenceAsc(Collection<String> tripIds);
}