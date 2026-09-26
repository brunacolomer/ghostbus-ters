package Entity;

import java.time.LocalDateTime;

public class Trip {
    private long id;
    private long routeId;
    private long busId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int nextStopIndex;
    private TripStatus status;

}
