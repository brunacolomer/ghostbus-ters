package com.example.ghostbusters.controller.dto;

import java.util.List;

public record RouteMapResponse(
        String id,
        String number,
        String name,
        String headsign,
        String color,
        List<double[]> coordinates,
        List<StopDto> stops
) {
    public record StopDto(String id, String name, double[] coordinates) {}
}