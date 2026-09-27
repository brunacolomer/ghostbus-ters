import { useEffect, useMemo, useState, type CSSProperties } from 'react'
import { getRouteSchedule, type RouteSchedule, getRouteActivity, type Bus, type BusRoute, type RouteActivity } from '../routes/getRoutes'
import './RoutePanel.css'

type Props = {
  route: BusRoute
  routes: BusRoute[]
  buses: Bus[] | null
  busError: boolean
  onClose: () => void
  onDirectionChange: (route: BusRoute) => void
  onFocus: (coordinates: [number, number]) => void
}

// Approximate distance along the actual shape, not a straight line between stops.
function positionBuses(route: BusRoute, buses: Bus[], variants: BusRoute[]) {
  const scale = Math.cos((route.coordinates[0]?.[1] ?? 26) * Math.PI / 180)
  const xy = ([lon, lat]: number[]) => [lon * 111320 * scale, lat * 111320]
  const points = route.coordinates.map(xy)
  const lengths = [0]
  for (let i = 1; i < points.length; i++) {
    lengths.push(lengths[i - 1] + Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]))
  }
  function project(coordinates: number[]) {
    const [x, y] = xy(coordinates)
    const candidates: { distance: number; along: number }[] = []
    for (let i = 1; i < points.length; i++) {
      const [ax, ay] = points[i - 1]
      const dx = points[i][0] - ax, dy = points[i][1] - ay
      const length = lengths[i] - lengths[i - 1]
      if (!length) continue
      const t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (length * length)))
      candidates.push({ distance: Math.hypot(x - ax - t * dx, y - ay - t * dy), along: lengths[i - 1] + t * length })
    }
    candidates.sort((a, b) => a.distance - b.distance)
    const best = candidates[0]
    // At loops/intersections, do not guess between distant parts of the journey.
    if (!best || best.distance > 150 || candidates.some((c) => c.distance < best.distance + 20 && Math.abs(c.along - best.along) > 300)) return null
    return best
  }
  const stops = route.stops.map((stop) => project(stop.coordinates))
  const ordered = stops.length > 0 && stops.every((stop, i) => stop && (!i || stop.along >= stops[i - 1]!.along))
  const placed: { bus: Bus; index: number; near: boolean; inferred: boolean }[] = []
  const unplaced: Bus[] = []
  function closestVariant(bus: Bus) {
    if (!Number.isFinite(bus.longitude) || !Number.isFinite(bus.latitude)) return undefined
    return variants.reduce<{ route: BusRoute; distance: number } | undefined>((closest, variant) => {
      const distance = variant.coordinates.reduce((best, [longitude, latitude]) =>
        Math.min(best, Math.hypot(longitude - bus.longitude, latitude - bus.latitude)), Infinity)
      return !closest || distance < closest.distance ? { route: variant, distance } : closest
    }, undefined)?.route
  }
  for (const bus of buses) {
    const position = Number.isFinite(bus.longitude) && Number.isFinite(bus.latitude) ? project([bus.longitude, bus.latitude]) : null
    const sameHeadsign = !!bus.headsign && !!route.headsign && bus.headsign.trim().toUpperCase() === route.headsign.trim().toUpperCase()
    const sameDirectionId = bus.directionId != null && route.directionId != null && bus.directionId === route.directionId
    const inferredVariant = !bus.headsign ? closestVariant(bus) : undefined
    const matchesDirection = sameHeadsign || sameDirectionId || inferredVariant?.id === route.id
    if (!ordered || !matchesDirection || !position) { unplaced.push(bus); continue }
    let nearest = 0
    stops.forEach((stop, i) => { if (Math.abs(stop!.along - position.along) < Math.abs(stops[nearest]!.along - position.along)) nearest = i })
    if (Math.abs(stops[nearest]!.along - position.along) <= 80) {
      placed.push({ bus, index: nearest, near: true, inferred: !sameHeadsign && !sameDirectionId })
    } else {
      const next = stops.findIndex((stop) => stop!.along > position.along)
      if (next <= 0) unplaced.push(bus)
      else placed.push({ bus, index: next - 1, near: false, inferred: !sameHeadsign && !sameDirectionId })
    }
  }
  return { placed, unplaced }
}

export function RoutePanel({ route, routes, buses, busError, onClose, onDirectionChange, onFocus }: Props) {
  const [activity, setActivity] = useState<RouteActivity | null>(null)
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 15_000)
    return () => window.clearInterval(timer)
  }, [])
  const [schedule, setSchedule] = useState<RouteSchedule | null>(null)
  const [scheduleError, setScheduleError] = useState(false)
  const [error, setError] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    let timer: number | undefined
    async function refresh() {
      try {
        const data = await getRouteActivity(route.routeId || route.id, controller.signal)
        if (controller.signal.aborted) return
        setActivity(data)
        setError(false)
      } catch {
        if (!controller.signal.aborted) setError(true)
      } finally {
        if (!controller.signal.aborted) timer = window.setTimeout(refresh, 60_000)
      }
    }
    void refresh()
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [route.routeId, route.id])

  useEffect(() => {
    const controller = new AbortController()
    let timer: number | undefined
    async function refresh() {
      try {
        const data = await getRouteSchedule(route, controller.signal)
        if (controller.signal.aborted) return
        setSchedule(data)
        setScheduleError(false)
      } catch {
        if (!controller.signal.aborted) setScheduleError(true)
      } finally {
        if (!controller.signal.aborted) timer = window.setTimeout(refresh, 60_000)
      }
    }
    void refresh()
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [route])

  function scheduledLabel(stopId: string) {
    if (scheduleError) return 'Schedule temporarily unavailable'
    if (!schedule) return 'Loading schedule…'
    if (!schedule.calendarAvailable) return 'Service calendar unavailable for this date'
    const arrival = schedule.arrivals[stopId]
    if (!arrival) return 'No scheduled arrival in the next 24 hours'
    const at = new Date(arrival)
    if (at.getTime() < now) return 'Refreshing schedule…'
    const time = at.toLocaleTimeString('en-US', { timeZone: 'America/New_York', hour: '2-digit', minute: '2-digit', hour12: false })
    const day = at.toLocaleDateString('en-US', { timeZone: 'America/New_York', month: 'short', day: 'numeric' })
    const minutes = Math.ceil((at.getTime() - now) / 60_000)
    return `Scheduled: ${time} · ${day} · in ${minutes} min`
  }

  const lineBuses = buses?.filter((bus) => bus.routeId === route.id || bus.line === route.number || bus.routeId === route.number)
  const routeVariants = routes.filter((item) => (item.routeId || item.id) === (route.routeId || route.id))
  const positions = useMemo(() => positionBuses(route, (buses ?? []).filter((bus) =>
    bus.routeId === route.id || bus.line === route.number || bus.routeId === route.number), routeVariants), [route, routeVariants, buses])
  function busButton(bus: Bus, label: string) {
    return <button className="route-bus" type="button" key={bus.busId}
      disabled={!Number.isFinite(bus.longitude) || !Number.isFinite(bus.latitude)}
      onClick={() => onFocus([bus.longitude, bus.latitude])}>
      <strong>Bus {bus.busId}</strong><span>{label}</span>
      <small>Position: {bus.lastUpdated.replace('T', ' ').slice(0, 19)}</small>
    </button>
  }
  const stopNames = new Map(route.stops.map((stop) => [stop.id, stop.name]))
  const misses = activity?.missedArrivals ?? []
  const directions = routes.filter((item, index, all) =>
    (item.routeId || item.id) === (route.routeId || route.id) &&
    all.findIndex((candidate) => candidate.id === item.id) === index)

  return (
    <aside className="route-panel" aria-labelledby="route-panel-title" style={{ '--line-color': route.color } as CSSProperties}>
      <header>
        <span className="route-panel-number" style={{ background: route.color }}>{route.number}</span>
        <h2 id="route-panel-title">{route.name}</h2>
        <button type="button" onClick={onClose} aria-label="Close route details">×</button>
      </header>
      <div className="route-panel-content">
        {directions.length > 1 && <nav className="route-directions" aria-label="Choose direction">
          {directions.map((direction) => (
            <button
              className={direction.id === route.id ? 'active' : ''}
              key={direction.id}
              type="button"
              onClick={() => onDirectionChange(direction)}
            >
              <span>{direction.headsign || `Direction ${direction.directionId ?? '?'}`}</span>
              <small>{direction.directionId == null ? 'Unknown direction' : `Direction ${direction.directionId}`}</small>
            </button>
          ))}
        </nav>}
        <p>Displayed journey: {route.headsign || 'Direction unavailable'}</p>
        <div className="route-panel-overview">
          <strong>{route.stops.length} stops</strong><span>{lineBuses ? `${lineBuses.length} observed buses` : 'Loading buses…'}</span>
        </div>
        <p className="route-panel-note">Bus placement is approximate, based on reported positions along this journey. Scheduled times use Miami time; they are not live arrival predictions.</p>
        {busError && <p role="status">Bus updates unavailable{buses ? ' · showing last received positions' : ''}.</p>}
        {lineBuses?.length === 0 && <p>No bus positions available for this line.</p>}
        <div className="route-panel-scroll">
          <ol className="route-panel-stops">
        {route.stops.map((stop, index) => (
          <li key={`${stop.id}-${index}`}>
            <button className="route-stop" type="button" onClick={() => onFocus(stop.coordinates)}>
              <span>{stop.name}</span>
              <small className="route-schedule">{scheduledLabel(stop.id)}</small>
              {misses.some((miss) => miss.stopId === stop.id) && <small className="route-panel-warning">Possible missed arrival · last hour</small>}
            </button>
            {positions.placed.filter((item) => item.index === index && item.near).map(({ bus, inferred }) => busButton(bus, inferred ? 'Likely direction · Near this stop' : 'Near this stop'))}
            <div className="route-between">
              {positions.placed.filter((item) => item.index === index && !item.near).map(({ bus, inferred }) => busButton(bus, `${inferred ? 'Likely direction · ' : ''}Between this stop and ${route.stops[index + 1].name}`))}
            </div>
          </li>
        ))}
          </ol>
          {positions.unplaced.length > 0 && <details className="route-other-buses">
        <summary>{positions.unplaced.length} buses outside this journey / position uncertain</summary>
        <p className="route-panel-note">Different or unknown direction, off the displayed shape, or an ambiguous position. Select to locate on the map.</p>
        {positions.unplaced.map((bus) => busButton(bus, bus.headsign || 'Direction unavailable'))}
          </details>}
          <section>
        <h3>Possible missed arrivals</h3>
        <p className="route-panel-note">Detected in the last hour, across this line’s trips. Missing observations do not confirm a cancellation.</p>
        {error && <p role="status">Activity updates unavailable{activity ? ' · showing last received records' : ''}.</p>}
        {!activity && !error && <p role="status">Loading recent activity…</p>}
        {activity && <p>{misses.length}{activity.truncated ? '+' : ''} missed stop records · checked at {activity.checkedAt.replace('T', ' ').slice(0, 19)}</p>}
        {activity?.truncated && <p className="route-panel-note">Showing the latest 100 records.</p>}
        <ul>
          {misses.map((miss) => (
            <li key={miss.id} className="route-panel-miss">
              <strong>{stopNames.get(miss.stopId) || `Stop ${miss.stopId}`}</strong>
              <span>Scheduled: {miss.scheduledTime} · Trip {miss.tripId}</span>
              <small>Detected: {miss.checkedAt.replace('T', ' ').slice(0, 19)}</small>
            </li>
          ))}
        </ul>
          </section>
        </div>
      </div>
    </aside>
  )
}
