package com.example.ghostbusters.controller;

import com.example.ghostbusters.entity.StopVisitStatus;
import com.example.ghostbusters.repository.StopVisitRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GhostBusControllerTest {
    private final StopVisitRepository repository = mock(StopVisitRepository.class);
    private final GhostBusController controller = new GhostBusController(repository);

    @Test
    void allTimeCountIncludesOlderMisses() {
        when(repository.countByStatus(StopVisitStatus.MISSED)).thenReturn(4000L);
        assertEquals(4000L, controller.getGhostBusCount(true).get("ghostBusCount"));
        verify(repository, never()).countByStatusAndCheckedAtAfter(any(), any());
    }

    @Test
    void defaultCountKeepsTheHourlyWindow() {
        when(repository.countByStatusAndCheckedAtAfter(eq(StopVisitStatus.MISSED), any())).thenReturn(10L);
        assertEquals(10L, controller.getGhostBusCount(false).get("ghostBusCount"));
        verify(repository, never()).countByStatus(any());
    }

    @Test
    void sinceUsesFirstMissAndAllowsEmptyHistory() {
        assertNull(controller.getCountingSince().since());
        var first = LocalDateTime.of(2026, 9, 25, 10, 30);
        when(repository.findFirstCheckedAtByStatus(StopVisitStatus.MISSED)).thenReturn(first);
        assertEquals(first, controller.getCountingSince().since());
    }
}
