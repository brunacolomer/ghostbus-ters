# Ghost Bus-ters 👻🚌

**Finding the buses that were scheduled, but never actually showed up.**

Ghost Bus-ters compares Miami-Dade's published bus schedules with real-time vehicle data to detect **ghost buses**: scheduled trips that appear in the timetable but never seem to arrive.

Built at **ShellHacks 2026** for the **Waymo challenge**, the project combines open transit data, real-time tracking and an interactive map to explore bus reliability across Miami-Dade.

[**Live app →**](https://ghostbus-ters.miami) · [**API →**](https://api.ghostbus-ters.miami) · [**Devpost →**](DEVPOST_URL)

---

## Demo


[![Watch the demo](https://img.youtube.com/vi/[VIDEO_ID](https://youtu.be/DPUPwCxo6mk)/maxresdefault.jpg)](https://www.youtube.com/watch?v=[VIDEO_ID](https://youtu.be/DPUPwCxo6mk))

---

## What it does

- 🗺️ Displays Miami-Dade routes, stops and live buses
- 👻 Detects scheduled trips that may never have arrived
- 📊 Calculates reliability statistics by route
- 🧭 Plans trips while taking route reliability into account
- ❄️ Sends reliability snapshots to Snowflake for further analysis

## How detection works

The backend combines two sources:

- **GTFS static data** → what buses are scheduled to do
- **Miami-Dade ArcGIS real-time data** → where buses actually are

`BusPollerService` stores live bus positions, while `GhostBusDetectionService` checks recently scheduled stops against that data.

The GTFS and live feeds use unrelated trip IDs, so buses are matched mainly using their **route and destination headsign**.

Previously missed stops are checked again so late buses can still be marked as visited. The detection is intentionally conservative: we'd rather miss some ghost buses than classify a bus as missing just because it is late.

---

## Architecture

```text
GTFS schedule ───────────┐
                        ├──► Spring Boot backend
ArcGIS live bus data ───┘          │
                                   ├──► PostgreSQL / TimescaleDB
                                   ├──► Snowflake
                                   │
                                   ▼
                             React + MapLibre
```

The frontend and backend are deployed on **DigitalOcean App Platform**.

## Tech stack

| | Technologies |
| --- | --- |
| Frontend | React, TypeScript, Vite, MapLibre GL |
| Backend | Java, Spring Boot, JPA, JDBC |
| Database | PostgreSQL / TimescaleDB — Tiger Data |
| Analytics | Snowflake |
| Infrastructure | DigitalOcean, Docker |
| Data | Miami-Dade GTFS + ArcGIS real-time API |

---

## Main API endpoints

| Endpoint | Description |
| --- | --- |
| `GET /api/routes` | Routes, shapes and stops |
| `GET /api/buses` | Live bus positions |
| `GET /api/ghost-buses/count` | Detected ghost trips |
| `GET /api/routes/reliability` | Reliability by route |
| `GET /api/routes/{routeId}/activity` | Recent route activity |
| `GET /api/routes/{routeId}/schedule` | Upcoming arrivals |
| `GET /api/routes/plan` | Reliability-aware trip planning |

Swagger docs are available at `/docs`.

---

## What's next?

A hackathon gives us enough data to demonstrate the idea, but not enough to properly measure long-term reliability.

With more data, Ghost Bus-ters could show which **routes, stops, days and times** consistently experience missing service and how those patterns change over time.

---

Built by students at **ShellHacks 2026** for the **Waymo challenge**.

[**View on Devpost →**](DEVPOST_URL)
