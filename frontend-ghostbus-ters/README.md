# Ghost Bus-ters

Minimal React + Vite + TypeScript dark bus map using MapLibre and OpenFreeMap's Dark style. Internet access and WebGL are required; no API key is needed.

```sh
npm install
npm run dev
```

The demo displays one direction of Miami route 2, with its actual shape and all 43 stops extracted from `google_transit.zip`. Hover or tap a route or stop for details. This is a static sample, not live service information. The line popup shows the sample stop count; a stop popup shows its name. The GTFS headsign belongs to this specific trip, not both directions of the bus line.

- `src/components/MapView.tsx`: map setup and loading state.
- `src/routes/mapRoutes.ts`: route lines, stop dots, hover highlight and fixed-size popup.
- `src/routes/getRoutes.ts`: response type and the single fetch function. Production uses `https://api.ghostbus-ters.miami` by default; local development can override it with `VITE_API_BASE_URL`.
- `public/mock/routes.json`: temporary endpoint response, derived from GTFS routes, trips, shapes, stop_times and stops.

To connect the backend, change the URL in `getRoutes` and remove `public/mock/routes.json`. The endpoint should return an array with this simplified format (one entry per route direction/variant, each with a unique ID):

```ts
{
  id: string
  number: string
  name: string
  headsign: string
  color: string // CSS hex color, e.g. #008000
  coordinates: [number, number][] // [longitude, latitude], ordered along the shape
  stops: {
    id: string
    name: string
    coordinates: [number, number]
  }[] // In travel order
}[]
```

Each shape must contain at least two coordinates. The frontend renders every returned entry and fits the map to their bounds. The backend handles GTFS joins and selects the relevant directions/variants. Configure CORS if the endpoint is hosted on a different origin.

To use the local backend, copy `.env.example` to `.env.local` and run the frontend normally. `.env.local` is ignored by git, so the repository default remains configured for production:

```sh
cp .env.example .env.local
npm run dev
```

Run `npm run build` and `npm run lint` to check the project.

The first-visit introduction explains scheduled versus live transit data and the current demo. It uses a native modal dialog with keyboard focus containment and Escape support. Dismissal is saved in localStorage (`ghostbus-introduction-seen`); About reopens it. Clear that key to replay the first visit. If storage is unavailable, the introduction still opens and closes normally.

The persistent ghost-bus summary refreshes every 60 seconds using `/api/ghost-buses/count?allTime=true` and `/api/ghost-buses/since`. The count represents MISSED stop-visit records, not distinct vehicles. `since` is the earliest MISSED record's `checked_at` (null when none exist); the card displays its recorded calendar date. The existing `/count` default still returns the last hour. Deploy the updated backend before using this summary; both calls use `VITE_API_BASE_URL` when set.

Selecting a route opens the route panel with ordered stops, observed buses across the line's directions, and recent possible missed arrivals. Stop and bus buttons locate the item on the map. Bus positions reuse the existing 20-second polling; activity refreshes every minute from `GET /api/routes/{routeId}/activity`. Deploy the updated backend to enable activity. This endpoint joins missed stop records to GTFS trips to identify the route, returns records checked in the last hour, and caps the list at 100 with a `truncated` flag. It does not change detection, confirm cancellations, or predict arrival times. Recorded timestamps are displayed as supplied by the backend, without assuming a timezone.

Scheduled stop arrivals are fetched every minute from `GET /api/routes/{routeId}/schedule?headsign=...`. This returns the next scheduled arrival per stop over the next 24 hours for trips with the displayed destination, as ISO timestamps. The panel labels these as Scheduled and formats them in America/New_York; these are not GPS arrival predictions. The backend reads `transit.trips`, `transit.stop_times`, `transit.calendar` and `transit.calendar_dates` from the database on each request. No ZIP or bundled calendar files are used. The calendar tables must be populated from the same GTFS release as the trips and stop times: `calendar` needs service_id, monday through sunday (0/1 or boolean), start_date and end_date; `calendar_dates` needs service_id, date and exception_type (1=added, 2=removed). Dates may be SQL DATE or YYYYMMDD values. Missing tables cause an error and the UI shows schedule unavailable; empty or expired calendars are not treated as active service. Calendar changes take effect on the next refresh without redeploying. GTFS times beyond 24:00 and the noon-minus-12-hours DST convention are handled according to https://gtfs.org/documentation/schedule/reference/.
