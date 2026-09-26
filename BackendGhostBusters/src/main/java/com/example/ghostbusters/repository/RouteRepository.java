package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, String> {
}