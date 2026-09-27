import { useEffect, useRef, useState } from 'react'
import { Map, NavigationControl, setWorkerUrl } from 'maplibre-gl'
import mapWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import { getBuses, getRoutes, type Bus, type BusRoute } from '../routes/getRoutes'
import { showRoutes, type RouteMap } from '../routes/mapRoutes'
import { GhostSummary } from './GhostSummary'
import { Introduction } from './Introduction'
import 'maplibre-gl/dist/maplibre-gl.css'

setWorkerUrl(mapWorkerUrl)

export function MapView() {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<Map | null>(null)
  const [dark, setDark] = useState(true)
  const [status, setStatus] = useState('Loading bus routes…')

  useEffect(() => {
    if (!container.current) return
    const controller = new AbortController()
    let routeMap: RouteMap | undefined
    let buses: Bus[] = []
    let refreshBuses: number | undefined
    const map = new Map({
      container: container.current,
      style: 'https://tiles.openfreemap.org/styles/dark',
      center: [-80.1918, 25.7617],
      zoom: 11.7,
    })
    mapRef.current = map
    let routes: BusRoute[] = []
    map.addControl(new NavigationControl(), 'top-right')
    map.on('error', (event) => console.error('Map loading error:', event.error))
    function drawRoutes() {
      routeMap?.clear()
      routeMap = showRoutes(map, routes, false)
      routeMap?.updateBuses(buses)
    }
    function pollBuses() {
      getBuses(controller.signal).then((data) => {
        if (controller.signal.aborted) return
        buses = data
        routeMap?.updateBuses(buses)
      }).catch(() => undefined)
    }
    map.on('style.load', () => {
      // Lift the basemap contrast while preserving its road widths and label sizes.
      for (const layer of map.getStyle().layers) {
        if (container.current?.parentElement?.dataset.theme !== 'dark') break
        if (layer.type === 'symbol' && layer.layout?.['text-field']) {
          map.setPaintProperty(layer.id, 'text-color', '#737d85')
          map.setPaintProperty(layer.id, 'text-halo-color', '#111518')
        }
        if (layer.type === 'line' && layer.id.startsWith('highway_')) {
          map.setPaintProperty(layer.id, 'line-color', layer.id.endsWith('_casing') ? '#30373d' : '#242a2f')
        }
      }
      drawRoutes()
    })
    getRoutes(controller.signal).then((data) => {
      if (controller.signal.aborted) return
      routes = data
      if (map.isStyleLoaded()) drawRoutes()
      pollBuses()
      refreshBuses = window.setInterval(pollBuses, 20_000)
      setStatus(routes.length ? '' : 'No bus routes available.')
    }).catch(() => {
      if (!controller.signal.aborted) setStatus('Could not load routes. Please refresh to try again.')
    })
    return () => {
      controller.abort()
      if (refreshBuses) window.clearInterval(refreshBuses)
      routeMap?.clear()
      map.remove()
      mapRef.current = null
    }
  }, [])

  function toggleTheme() {
    mapRef.current?.setStyle(`https://tiles.openfreemap.org/styles/${dark ? 'positron' : 'dark'}`)
    setDark(!dark)
  }

  return (
    <main className="map-view" data-theme={dark ? 'dark' : 'light'}>
      <div ref={container} className="map" aria-label="Map of Miami bus routes" />
      <button className="theme-toggle" type="button" onClick={toggleTheme} aria-label="Dark mode" aria-pressed={dark}>
        {dark ? '☀ Light mode' : '☾ Dark mode'}
      </button>
      <GhostSummary />
      <Introduction />
      {status && <p className="map-status" role="status">{status}</p>}
    </main>
  )
}
