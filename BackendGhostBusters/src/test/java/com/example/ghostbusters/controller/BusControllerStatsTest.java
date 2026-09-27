package com.example.ghostbusters.controller;
import com.example.ghostbusters.entity.Bus;
import com.example.ghostbusters.repository.BusRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BusControllerStatsTest {
    @Test void separatesRecentAndOldObservations() {
        var repository = mock(BusRepository.class);
        var recent = new Bus();
        recent.setSpeed(12.0);
        recent.setLastUpdated(LocalDateTime.now(ZoneId.of("America/New_York")).minusSeconds(30));
        var old = new Bus();
        old.setSpeed(15.0);
        old.setLastUpdated(LocalDateTime.now(ZoneId.of("America/New_York")).minusMinutes(5));
        var unknown = new Bus();
        when(repository.findAll()).thenReturn(List.of(recent, old, unknown));
        var result = new BusController(repository).getBusPositions();
        assertEquals(3, result.size());
        assertTrue(result.get(0).observedRecently());
        assertEquals(12.0, result.get(0).speed());
        assertFalse(result.get(1).observedRecently());
        assertFalse(result.get(2).observedRecently());
        assertNull(result.get(2).speed());
    }
}
