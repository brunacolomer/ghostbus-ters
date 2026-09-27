import type { BusRoute } from './getRoutes'

export type Journey = {
  demo: boolean
  minutes: number
  steps: { line: string; instruction: string; color: string }[]
  routes: { id: string; number: string; color: string; coordinates: [number, number][] }[]
}

// Future backend contract: POST { fromStopId, toStopId } -> Journey.
// Available routes are only used to illustrate the mock, not to calculate a journey.
export async function planJourney(
  request: { fromStopId: string; toStopId: string },
  signal: AbortSignal,
  availableRoutes: BusRoute[],
): Promise<Journey> {
  signal.throwIfAborted()
  if (!request.fromStopId || !request.toStopId || request.fromStopId === request.toStopId) {
    throw new Error('Choose two different stations.')
  }
  const candidates = availableRoutes.filter((route) => route.coordinates.length >= 2)
  const first = candidates.find((route) => route.stops.some((stop) => stop.id === request.fromStopId))
  const last = candidates.find((route) => route.stops.some((stop) => stop.id === request.toStopId) && route.number !== first?.number)
    ?? candidates.find((route) => route.stops.some((stop) => stop.id === request.toStopId))
  const routes = [...new Map([first, last].filter((route): route is BusRoute => !!route).map((route) => [route.id, route])).values()]
  if (!routes.length) throw new Error('Route geometry is not available yet.')
  return {
    demo: true,
    minutes: 25,
    routes: routes.map(({ id, number, color, coordinates }) => ({ id, number, color, coordinates })),
    steps: routes.map((route, index) => ({
      line: `Line ${route.number}`,
      color: route.color,
      instruction: index === 0 ? 'First leg · illustrative only' : 'Transfer to this line · connection not verified',
    })),
  }
}
