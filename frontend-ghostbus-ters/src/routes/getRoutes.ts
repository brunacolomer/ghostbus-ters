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
