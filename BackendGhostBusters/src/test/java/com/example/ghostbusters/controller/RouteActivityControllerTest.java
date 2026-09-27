package com.example.ghostbusters.controller;

import com.example.ghostbusters.entity.StopVisit;
import com.example.ghostbusters.entity.StopVisitStatus;
import com.example.ghostbusters.repository.RouteRepository;
import com.example.ghostbusters.repository.StopVisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RouteActivityControllerTest {
    private final StopVisitRepository visits = mock(StopVisitRepository.class);
    private final RouteRepository routes = mock(RouteRepository.class);
    private final RouteActivityController controller = new RouteActivityController(visits, routes);

    @Test
    void unknownRouteIsNotFound() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.getActivity("missing")).getStatusCode().value());
        verifyNoInteractions(visits);
    }

    @Test
    void emptyActivityIsValidAndUsesLastHour() {
        when(routes.existsById("2")).thenReturn(true);
        when(visits.findRouteActivity(eq("2"), eq(StopVisitStatus.MISSED), any(), any())).thenReturn(List.of());
        var response = controller.getActivity("2");
        assertTrue(response.missedArrivals().isEmpty());
        assertFalse(response.truncated());
        verify(visits).findRouteActivity(eq("2"), eq(StopVisitStatus.MISSED),
                eq(response.checkedAt().minusHours(1)), argThat((Pageable page) -> page.getPageSize() == 101));
    }

    @Test
    void responseIsBoundedAndPreservesObservationTimes() {
        when(routes.existsById("2")).thenReturn(true);
        var visit = new StopVisit();
        visit.setId(1L);
        visit.setStopId("stop");
        visit.setScheduledTime("12:00:00");
        visit.setCheckedAt(LocalDateTime.of(2026, 9, 26, 12, 15));
        when(visits.findRouteActivity(eq("2"), eq(StopVisitStatus.MISSED), any(), any()))
                .thenReturn(Collections.nCopies(101, visit));
        var response = controller.getActivity("2");
        assertTrue(response.truncated());
        assertEquals(100, response.missedArrivals().size());
        assertEquals(visit.getCheckedAt(), response.missedArrivals().getFirst().checkedAt());
        assertEquals("12:00:00", response.missedArrivals().getFirst().scheduledTime());
    }
}
