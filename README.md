# ghostbus-ters
👻 Ghost Bus-ters

Finding the buses that were never actually coming.

Miami-Dade's transit network leans hard on buses — its Metrorail only has two lines (Green and Orange), so buses cover most of the county rail never reaches. Riders already deal with traffic delays, but there's a harder-to-see problem underneath that: ghost buses — trips that are scheduled to serve a stop but never actually show up.

Nobody analyzes schedule data against real vehicle data together, so nobody can say how often it happens, where, or which routes are worst. Ghost Bus-ters combines Miami-Dade's published GTFS schedule with its live ArcGIS bus-tracking feed to detect that gap automatically, and turns it into an interactive map anyone can explore.

Live app: ghostbus-ters.miami API: api.ghostbus-ters.miami

What it does
Renders every Miami-Dade bus route on an interactive map, with live vehicle positions
Continuously compares the static schedule against real GPS data to flag stops a bus never reached
Surfaces a running count of distinct ghost trips, and a per-route reliability breakdown
Plans multi-leg trips between two stops, weighted by each leg's live reliability score
Syncs reliability snapshots to Snowflake for downstream analytics
How detection actually works
BusPollerService polls Miami-Dade's ArcGIS real-time feed every 20 seconds and stores current vehicle positions.
GhostBusDetectionService runs every 2 minutes:
Computes a 10–30 minute lookback window (in seconds-since-midnight, so it doesn't break across midnight)
Pulls every scheduled stop due in that window directly from the database (findDueInWindow), rather than loading the entire schedule into memory
Skips stops already confirmed VISITED, but keeps re-checking previously MISSED ones — so a bus running a few minutes late still gets credit once it actually arrives
Matches live vehicles to scheduled trips on route number + destination headsign — the live feed's trip IDs and the GTFS trip IDs are two unrelated numbering systems, so headsign text is the actual bridge between "a real bus out there" and "a line in the timetable"
Batch-writes every result straight through JDBC rather than one row at a time through JPA
GET /api/ghost-buses/count reports the number of distinct trips that have never once been confirmed visited since a stored checkpoint — not a raw count of missed stop-checks, which would wildly overstate the problem.

A stop only becomes a "ghost" when no live vehicle anywhere is reporting that route + destination combination — a deliberately conservative bar, so the system is more likely to under-count real ghosts than to falsely accuse a bus that's simply running late.

Architecture
Miami-Dade GTFS (static)  ──┐
                             ├──►  Spring Boot backend  ──►  PostgreSQL / TimescaleDB
Miami-Dade ArcGIS (live)  ──┘         (DigitalOcean)          (Tiger Data Cloud)
                                          │
                                          ├──► Snowflake (reliability analytics)
                                          │
                                          ▼
                              React + MapLibre GL frontend
                                  (DigitalOcean Static Site)
Tech stack

Backend: Java · Spring Boot · Spring Data JPA / JDBC Frontend: React · TypeScript · Vite · MapLibre GL Database: PostgreSQL / TimescaleDB (Tiger Data Cloud) Analytics: Snowflake Infra: DigitalOcean App Platform, custom domain + DNS Data sources: Miami-Dade GTFS static feed, Miami-Dade ArcGIS Bus Real-Time REST API

Backend structure
Layer	Purpose
entity/	One class per database table — Bus, Route, ScheduledTrip, StopTime, Shape, Stop, StopVisit, GhostCountSettings
repository/	All database access, including the route+headsign matching query and the reliability aggregations
service/	The two scheduled jobs (BusPollerService, GhostBusDetectionService), plus RoutePlannerService, RouteScheduleService, and SnowflakeSyncService
controller/	The actual API surface — the only layer the frontend can reach
API endpoints
Method & path	What it returns
GET /health	Basic liveness check
GET /api/routes	All routes, shapes, and stops for the map
GET /api/routes/plan?fromStopId=&toStopId=	A multi-leg trip plan with reliability scoring
GET /api/buses	Live vehicle positions
GET /api/ghost-buses/count	Distinct ghost-trip count since the last reset
POST /api/ghost-buses/reset	Resets the counting checkpoint
GET /api/routes/{routeId}/activity	Recent missed arrivals for one route
GET /api/routes/{routeId}/schedule?headsign=	Upcoming scheduled arrivals
GET /api/routes/reliability	Miss-rate percentage per route
Running it locally

Backend (BackendGhostBusters/) needs src/main/resources/application.properties with:

properties
spring.datasource.url=jdbc:postgresql://<host>:<port>/<db>?currentSchema=transit&sslmode=require
spring.datasource.username=<user>
spring.datasource.password=<password>
snowflake.url=jdbc:snowflake://<account>.snowflakecomputing.com/?warehouse=<wh>&db=<db>&schema=<schema>
snowflake.user=<user>
snowflake.password=<password>

Then: ./gradlew bootRun

Frontend (frontend-ghostbus-ters/) needs a .env with:

VITE_API_BASE_URL=http://localhost:8080

Then: npm install && npm run dev

Challenges
Timezone mismatch: the backend's host environment ran in UTC while the transit data is all Miami local time, which briefly made real-time buses look hours later than they actually were until every timestamp was explicitly normalized.
Mismatched identifiers: the GTFS schedule and the live ArcGIS feed don't share trip or route IDs — matching had to go through route number + headsign text instead.
Time: a hackathon-length data collection window isn't enough to build the kind of long-term reliability statistics this problem really calls for — the system is built to keep accumulating data past the event.
What's next

With more time collecting live data, Ghost Bus-ters could surface which routes and stops are least reliable, which times of day are worst, and how reliability trends over weeks or months — turning this from a snapshot into something riders and the transit agency could actually act on.

Built with

API · DigitalOcean · Docker · GTFS · Java · MapLibre · PostgreSQL · React · REST · Snowflake · Spring Boot · SQL · Tiger Data · TypeScript · Vite
