package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

public interface BusRepository extends JpaRepository<Bus, Long> {
    @Modifying
    @Transactional
    @Query("delete from Bus b where b.busId not in :busIds")
    void deleteByBusIdNotIn(Collection<Long> busIds);
}
