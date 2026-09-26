package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.Bus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusRepository extends JpaRepository<Bus, Long> {
}