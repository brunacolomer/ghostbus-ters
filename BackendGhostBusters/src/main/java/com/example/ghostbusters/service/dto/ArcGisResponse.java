package com.example.ghostbusters.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArcGisResponse(List<ArcGisFeature> features) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArcGisFeature(ArcGisAttributes attributes, ArcGisGeometry geometry) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArcGisAttributes(
            Long BusID,
            String RouteID,
            String TripID,
            Double OnTime,
            Double vehSpeed
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArcGisGeometry(Double x, Double y) {}
}