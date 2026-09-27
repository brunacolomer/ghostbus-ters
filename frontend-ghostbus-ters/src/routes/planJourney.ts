import { apiUrl, type BusRoute } from './getRoutes'

type Segment = {
  routeId: string
  fromStopId: string
  fromStopName: string
  toStopId: string
  toStopName: string
  reliability: number
}
export type Journey = {
  reliability: number
  steps: { line: string; instruction: string; color: string }[]
  routes: { color: string; coordinates: [number, number][] }[]
}

// Clip a matching direction's existing geometry; never draw an unrelated full line.
function segmentCoordinates(route: BusRoute, from: string, to: string): [number, number][] {
  const start = route.stops.findIndex((stop) => stop.id === from)
  const end = route.stops.findIndex((stop, index) => index > start && stop.id === to)
  if (start < 0 || end < 0 || route.coordinates.length < 2) return []
  let cursor = 0
  let first = 0
  for (let i = 0; i <= end; i++) {
    const point = route.stops[i].coordinates
    let nearest = cursor
    let distance = Infinity
    for (let j = cursor; j < route.coordinates.length; j++) {
      const candidate = route.coordinates[j]
      const squared = ((candidate[0] - point[0]) * Math.cos(point[1] * Math.PI / 180)) ** 2
        + (candidate[1] - point[1]) ** 2
      if (squared < distance) { distance = squared; nearest = j }
    }
    cursor = nearest
    if (i === start) first = cursor
  }
  return route.coordinates.slice(first, cursor + 1)
}

export async function planJourney(
  request: { fromStopId: string; toStopId: string },
  signal: AbortSignal,
  availableRoutes: BusRoute[],
): Promise<Journey> {
  const response = await fetch(`${apiUrl.replace(/\/$/, '')}/api/routes/plan?${new URLSearchParams(request)}`, { signal })
  if (response.status === 404) throw new Error('No route found within two transfers.')
  if (!response.ok) throw new Error('Could not plan this journey. Please try again.')
  const plan: { segments: Segment[]; reliability: number } = await response.json()
  const routes: Journey['routes'] = []
  const steps = plan.segments.map((segment) => {
    const candidates = availableRoutes.filter((route) => (route.routeId || route.id) === segment.routeId)
    const color = candidates[0]?.color || '#3b82f6'
    const coordinates = candidates.map((route) => segmentCoordinates(route, segment.fromStopId, segment.toStopId))
      .find((points) => points.length >= 2)
    if (coordinates) routes.push({ color, coordinates })
    return {
      line: `Line ${candidates[0]?.number || segment.routeId}`,
      color,
      instruction: `${segment.fromStopName} → ${segment.toStopName} · ${Math.round(segment.reliability * 100)}% reliability score${coordinates ? '' : ' · Map geometry unavailable'}`,
    }
  })
  return { reliability: plan.reliability, steps, routes }
}
