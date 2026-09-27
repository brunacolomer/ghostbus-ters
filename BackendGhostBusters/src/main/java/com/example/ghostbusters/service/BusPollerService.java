package com.example.ghostbusters.service;

import com.example.ghostbusters.entity.Bus;
import com.example.ghostbusters.entity.BusPosition;
import com.example.ghostbusters.repository.BusRepository;
import com.example.ghostbusters.repository.BusPositionRepository;
import com.example.ghostbusters.service.dto.ArcGisResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Service
public class BusPollerService {

    private static final String ARC_GIS_URL =
            "https://gis.miamidade.gov/arcgis/rest/services/BusMetro_RealTime/BusRealTime/MapServer/0/query?where=1=1&outFields=*&f=json";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private BusRepository busRepository;

    @Autowired
    private BusPositionRepository busPositionRepository;

    @Scheduled(fixedRate = 20000) // every 20 seconds
    public void pollLiveBuses() {
        try {
            ArcGisResponse response = restTemplate.getForObject(ARC_GIS_URL, ArcGisResponse.class);

            if (response == null || response.features() == null) {
                System.out.println(">>> Bus poll: no data returned");
                return;
            }

            int saved = 0;
            LocalDateTime now = LocalDateTime.now();

            for (ArcGisResponse.ArcGisFeature feature : response.features()) {
                var attrs = feature.attributes();
                var geom = feature.geometry();
                if (attrs == null || attrs.BusID() == null || geom == null) continue;

                Bus bus = new Bus();
                bus.setBusId(attrs.BusID());
                bus.setRouteId(attrs.RouteID());
                bus.setTripId(attrs.TripID());
                bus.setTripHeadsign(attrs.TripHeadsign());
                bus.setLongitude(geom.x());
                bus.setLatitude(geom.y());
                bus.setOnTime(attrs.OnTime());
                bus.setSpeed(attrs.vehSpeed());
                bus.setLastUpdated(now);
                busRepository.save(bus);

                BusPosition position = new BusPosition();
                position.setBusId(attrs.BusID());
                position.setRouteId(attrs.RouteID());
                position.setTripHeadsign(attrs.TripHeadsign());
                position.setLatitude(geom.y());
                position.setLongitude(geom.x());
                position.setRecordedAt(now);
                busPositionRepository.save(position);

                saved++;
            }
            System.out.println(">>> Bus poll: updated " + saved + " buses at " + now);

        } catch (Exception e) {
            System.out.println(">>> Bus poll FAILED: " + e.getMessage());
        }
    }
}