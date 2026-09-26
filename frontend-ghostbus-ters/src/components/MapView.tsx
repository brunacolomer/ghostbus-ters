import { useEffect, useRef } from 'react'
import { Map, NavigationControl, setWorkerUrl } from 'maplibre-gl'
import mapWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import 'maplibre-gl/dist/maplibre-gl.css'

setWorkerUrl(mapWorkerUrl)

export function MapView() {
  const container = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!container.current) return

    const map = new Map({
      container: container.current,
      style: 'https://tiles.openfreemap.org/styles/positron',
      center: [-80.1918, 25.7617],
      zoom: 11.7,
    })

    map.addControl(new NavigationControl(), 'top-right')
    map.on('error', (event) => console.error('Map loading error:', event.error))

    return () => map.remove()
  }, [])

  return <main ref={container} className="map" aria-label="Map of Miami" />
}
