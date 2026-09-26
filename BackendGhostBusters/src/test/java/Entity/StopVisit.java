package Entity;

import java.time.LocalDateTime;

public class StopVisit {
    private long id;
    private long tripId;
    private long stopId;
    private LocalDateTime timeStamp;
    private StopVisitStatus status;
}
