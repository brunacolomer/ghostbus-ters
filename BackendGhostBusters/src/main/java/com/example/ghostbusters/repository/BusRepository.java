package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.Bus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface BusRepository extends JpaRepository<Bus, Long> {
	void deleteByBusIdNotIn(Collection<Long> busIds);
}