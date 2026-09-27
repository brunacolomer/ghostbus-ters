package com.example.ghostbusters.controller.dto;

import java.util.List;

public record RouteMapResponse(
        String id,
    String routeId,
        String number,
        String name,
        String headsign,
    Integer directionId,
        String color,
        List<double[]> coordinates,
        List<StopDto> stops
) {
    public record StopDto(String id, String name, double[] coordinates) {}
}