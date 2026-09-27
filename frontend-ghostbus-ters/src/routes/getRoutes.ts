export type BusRoute = {
  id: string
  routeId?: string
  number: string
  name: string
  headsign: string
  directionId?: number | null
  color: string
  coordinates: [number, number][] // Longitude, latitude (GeoJSON order).
  stops: { id: string; name: string; coordinates: [number, number] }[]
}

export type Bus = {
  busId: number
  latitude: number
  longitude: number
  routeId: string | null
  line: string | null
  tripId: string | null
  directionId: number | null
  headsign: string | null
  lastUpdated: string
  speed?: number | null
  observedRecently?: boolean
}

const productionApiUrl = 'https://api.ghostbus-ters.miami'
const apiUrl = import.meta.env.VITE_API_BASE_URL || productionApiUrl

export async function getRoutes(signal: AbortSignal): Promise<BusRoute[]> {
  const response = await fetch(`${apiUrl.replace(/\/$/, '')}/api/routes`, { signal })
  if (!response.ok) throw new Error('Could not load bus routes')
  return response.json()
}

export async function getBuses(signal: AbortSignal): Promise<Bus[]> {
  const response = await fetch(`${apiUrl.replace(/\/$/, '')}/api/buses`, { signal })
  if (!response.ok) throw new Error('Could not load buses')
  return response.json()
}

export async function getGhostSummary(signal: AbortSignal): Promise<{ count: number }> {
  const response = await fetch(`${apiUrl.replace(/\/$/, '')}/api/ghost-buses/count`, { signal })
  if (!response.ok) throw new Error('Could not load ghost bus count')
  const data = await response.json()
  return { count: data.ghostBusCount }
}

export type RouteActivity = {
  checkedAt: string
  truncated: boolean
  missedArrivals: { id: number; stopId: string; tripId: string; scheduledTime: string; checkedAt: string }[]
}

export async function getRouteActivity(routeId: string, signal: AbortSignal): Promise<RouteActivity> {
  const response = await fetch(`${apiUrl.replace(/\/$/, '')}/api/routes/${encodeURIComponent(routeId)}/activity`, { signal })
  if (!response.ok) throw new Error('Could not load route activity')
  return response.json()
}

export type RouteSchedule = {
  generatedAt: string
  calendarAvailable: boolean
  arrivals: Record<string, string>
}

export async function getRouteSchedule(route: BusRoute, signal: AbortSignal): Promise<RouteSchedule> {
  const response = await fetch(`${apiUrl.replace(/\/$/, '')}/api/routes/${encodeURIComponent(route.routeId || route.id)}/schedule?headsign=${encodeURIComponent(route.headsign)}`, { signal })
  if (!response.ok) throw new Error('Could not load scheduled arrivals')
  return response.json()
}

export type RouteReliability = {
  missedCount: number
  totalChecked: number
  missRatePercent: number
}

export type ReliabilityRow = RouteReliability & { routeId: string; number: string }

export async function getReliabilityOverview(signal: AbortSignal): Promise<ReliabilityRow[]> {
  const response = await fetch(`${apiUrl.replace(/\/$/, '')}/api/routes/reliability`, { signal })
  if (!response.ok) throw new Error('Could not load reliability')
  const rows: Record<string, string | number>[] = await response.json()
  return rows.map((row) => {
    // PostgreSQL may lowercase the native query's aliases.
    const result = {
      routeId: String(row.routeId ?? row.routeid),
      number: String(row.routeShortName ?? row.routeshortname ?? row.routeId ?? row.routeid),
      missedCount: Number(row.missedCount ?? row.missedcount),
      totalChecked: Number(row.totalChecked ?? row.totalchecked),
      missRatePercent: Number(row.missRatePercent ?? row.missratepercent),
    }
    if (![result.missedCount, result.totalChecked, result.missRatePercent].every(Number.isFinite)
      || result.totalChecked < 0 || result.missedCount < 0 || result.missedCount > result.totalChecked
      || result.missRatePercent < 0 || result.missRatePercent > 100) throw new Error('Invalid reliability response')
    return result
  }).filter((row) => row.totalChecked > 0)
}

export async function getRouteReliability(routeId: string, signal: AbortSignal): Promise<RouteReliability | null> {
  return (await getReliabilityOverview(signal)).find((row) => row.routeId === routeId) ?? null
}
