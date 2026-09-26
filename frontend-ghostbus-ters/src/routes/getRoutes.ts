export type BusRoute = {
  id: string
  number: string
  name: string
  headsign: string
  color: string
  coordinates: [number, number][] // Longitude, latitude (GeoJSON order).
  stops: { id: string; name: string; coordinates: [number, number] }[]
}

export async function getRoutes(signal: AbortSignal): Promise<BusRoute[]> {
  // Replace this URL with the backend endpoint when it is ready.
  const response = await fetch(`${import.meta.env.BASE_URL}mock/routes.json`, { signal })
  if (!response.ok) throw new Error('Could not load bus routes')
  return response.json()
}
