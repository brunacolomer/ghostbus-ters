package com.example.ghostbusters.repository;

import com.example.ghostbusters.entity.Shape;
import com.example.ghostbusters.entity.ShapePointId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ShapeRepository extends JpaRepository<Shape, ShapePointId> {
    List<Shape> findByShapeIdOrderByShapePtSequenceAsc(String shapeId);
    List<Shape> findByShapeIdInOrderByShapePtSequenceAsc(Collection<String> shapeIds);
}