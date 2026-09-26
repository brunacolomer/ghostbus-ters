export type BusRoute = {
  id: string
  number: string
  name: string
  headsign: string
  color: string
  coordinates: [number, number][] // Longitude, latitude (GeoJSON order).
  stops: { id: string; name: string; coordinates: [number, number] }[]
}

const productionApiUrl = 'https://api.ghostbus-ters.miami'
const apiUrl = import.meta.env.VITE_API_BASE_URL || productionApiUrl

export async function getRoutes(signal: AbortSignal): Promise<BusRoute[]> {
  const response = await fetch(`${apiUrl.replace(/\/$/, '')}/api/routes`, { signal })
  if (!response.ok) throw new Error('Could not load bus routes')
  return response.json()
}
