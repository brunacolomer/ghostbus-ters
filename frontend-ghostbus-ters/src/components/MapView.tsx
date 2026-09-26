import { useEffect, useRef, useState } from 'react'
import { Map, NavigationControl, setWorkerUrl } from 'maplibre-gl'
import mapWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import { getRoutes } from '../routes/getRoutes'
import { showRoutes } from '../routes/mapRoutes'
import 'maplibre-gl/dist/maplibre-gl.css'

setWorkerUrl(mapWorkerUrl)

export function MapView() {
  const container = useRef<HTMLDivElement>(null)
  const [status, setStatus] = useState('Loading bus routes…')

  useEffect(() => {
    if (!container.current) return
    const controller = new AbortController()
    let clearRoutes: (() => void) | undefined
    const map = new Map({
      container: container.current,
      style: 'https://tiles.openfreemap.org/styles/dark',
      center: [-80.1918, 25.7617],
      zoom: 11.7,
    })
    map.addControl(new NavigationControl(), 'top-right')
    map.on('error', (event) => console.error('Map loading error:', event.error))
    map.on('load', async () => {
      // Lift the basemap contrast while preserving its road widths and label sizes.
      for (const layer of map.getStyle().layers) {
        if (layer.type === 'symbol' && layer.layout?.['text-field']) {
          map.setPaintProperty(layer.id, 'text-color', '#737d85')
          map.setPaintProperty(layer.id, 'text-halo-color', '#111518')
        }
        if (layer.type === 'line' && layer.id.startsWith('highway_')) {
          map.setPaintProperty(layer.id, 'line-color', layer.id.endsWith('_casing') ? '#30373d' : '#242a2f')
        }
      }
      try {
        const routes = await getRoutes(controller.signal)
        if (controller.signal.aborted) return
        clearRoutes = showRoutes(map, routes)
        setStatus(routes.length ? '' : 'No bus routes available.')
      } catch {
        if (!controller.signal.aborted) setStatus('Could not load routes. Please refresh to try again.')
      }
    })
    return () => {
      controller.abort()
      clearRoutes?.()
      map.remove()
    }
  }, [])

  return (
    <main className="map-view">
      <div ref={container} className="map" aria-label="Map of Miami bus routes" />
      {status && <p className="map-status" role="status">{status}</p>}
    </main>
  )
}
