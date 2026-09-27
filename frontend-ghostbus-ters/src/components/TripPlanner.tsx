import { useEffect, useMemo, useRef, useState, type FormEvent, type RefObject } from 'react'
import { Map as TransitMap, LngLatBounds, Marker, Popup, type MapMouseEvent } from 'maplibre-gl'
import type { BusRoute } from '../routes/getRoutes'
import { planJourney, type Journey } from '../routes/planJourney'
import './TripPlanner.css'

export function TripPlanner({ routes, hidden, mapRef }: { routes: BusRoute[]; hidden: boolean; mapRef: RefObject<TransitMap | null> }) {
  const stops = useMemo(() => [...new Map(routes.flatMap((route) => route.stops.map((stop) => ({ ...stop, color: route.color }))).map((stop) => [stop.id, stop])).values()]
    .sort((a, b) => a.name.localeCompare(b.name)), [routes])
  const [picking, setPicking] = useState<'from' | 'to' | null>(null)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [journey, setJourney] = useState<Journey | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const pending = useRef<AbortController | null>(null)
  useEffect(() => () => pending.current?.abort(), [])

  useEffect(() => {
    const map = mapRef.current
    if (!map || hidden) return
    const markers = [from, to].flatMap((id, index) => {
      const stop = stops.find((item) => item.id === id)
      if (!stop) return []
      return [new Marker({ color: index === 0 ? '#38b88c' : '#f59e0b' })
        .setLngLat(stop.coordinates).setPopup(new Popup().setText(`${index === 0 ? 'From' : 'To'}: ${stop.name}`)).addTo(map)]
    })
    return () => markers.forEach((marker) => marker.remove())
  }, [from, to, stops, hidden, mapRef])

  useEffect(() => {
    const map = mapRef.current
    if (!map || hidden || !picking) return
    const popup = new Popup({ closeButton: false, offset: 12 })
    function addStops() {
      if (map!.getSource('planner-stops')) return
      map!.addSource('planner-stops', {
        type: 'geojson', data: { type: 'FeatureCollection', features: stops.map((stop) => ({
          type: 'Feature', properties: { id: stop.id, name: stop.name, color: stop.color },
          geometry: { type: 'Point', coordinates: stop.coordinates },
        })) },
      })
      map!.addLayer({ id: 'planner-stops', type: 'circle', source: 'planner-stops',
        paint: { 'circle-radius': ['interpolate', ['linear'], ['zoom'], 10, 2, 15, 4],
          'circle-color': ['get', 'color'], 'circle-opacity': 0.4,
          'circle-stroke-color': ['get', 'color'], 'circle-stroke-opacity': 0.65, 'circle-stroke-width': 1 } })
    }
    function explore(event: MapMouseEvent) {
      if (!map!.getLayer('planner-stops')) return
      const feature = map!.queryRenderedFeatures([[event.point.x - 5, event.point.y - 5], [event.point.x + 5, event.point.y + 5]], { layers: ['planner-stops'] })[0]
      map!.getCanvas().style.cursor = feature ? 'pointer' : 'crosshair'
      if (!feature) { popup.remove(); return }
      popup.setLngLat(event.lngLat).setText(feature.properties.name).addTo(map!)
      if (event.type !== 'click') return
      const id = String(feature.properties.id)
      if (id === (picking === 'from' ? to : from)) {
        setError('Choose two different stops.')
        return
      }
      pending.current?.abort()
      setLoading(false)
      setJourney(null)
      setError('')
      if (picking === 'from') { setFrom(id); setPicking(to ? null : 'to') }
      else { setTo(id); setPicking(null) }
    }
    if (map.isStyleLoaded()) addStops()
    map.on('style.load', addStops)
    map.on('mousemove', explore)
    map.on('click', explore)
    return () => {
      map.off('style.load', addStops)
      map.off('mousemove', explore)
      map.off('click', explore)
      popup.remove()
      map.getCanvas().style.cursor = ''
      if (map.getLayer('planner-stops')) map.removeLayer('planner-stops')
      if (map.getSource('planner-stops')) map.removeSource('planner-stops')
    }
  }, [picking, stops, from, to, hidden, mapRef])

  useEffect(() => {
    const map = mapRef.current
    if (!map || hidden || picking || !journey) return
    const previousOpacity = map.getLayer('bus-lines') ? map.getPaintProperty('bus-lines', 'line-opacity') : 0.5
    let fitted = false
    function draw() {
      if (map!.getSource('planner-journey')) return
      map!.addSource('planner-journey', { type: 'geojson', data: {
        type: 'FeatureCollection', features: journey!.routes.map((route) => ({
          type: 'Feature', properties: { color: route.color },
          geometry: { type: 'LineString', coordinates: route.coordinates },
        })),
      } })
      map!.addLayer({ id: 'planner-journey', type: 'line', source: 'planner-journey',
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': ['get', 'color'], 'line-width': 5, 'line-opacity': 0.9 } })
      if (map!.getLayer('bus-lines')) map!.setPaintProperty('bus-lines', 'line-opacity', 0.08)
      if (!fitted) {
        const bounds = new LngLatBounds()
        journey!.routes.forEach((route) => route.coordinates.forEach((point) => bounds.extend(point)))
        if (!bounds.isEmpty()) map!.fitBounds(bounds, { padding: window.innerWidth > 800
          ? { left: 420, right: 70, top: 180, bottom: 80 } : 60, maxZoom: 13 })
        fitted = true
      }
    }
    if (map.isStyleLoaded()) draw()
    map.on('style.load', draw)
    return () => {
      map.off('style.load', draw)
      if (map.getLayer('planner-journey')) map.removeLayer('planner-journey')
      if (map.getSource('planner-journey')) map.removeSource('planner-journey')
      if (map.getLayer('bus-lines') && map.getPaintProperty('bus-lines', 'line-opacity') === 0.08) {
        map.setPaintProperty('bus-lines', 'line-opacity', previousOpacity)
      }
    }
  }, [journey, hidden, picking, mapRef])

  async function submit(event: FormEvent) {
    event.preventDefault()
    pending.current?.abort()
    const controller = new AbortController()
    pending.current = controller
    setLoading(true)
    setError('')
    setJourney(null)
    try {
      const result = await planJourney({ fromStopId: from, toStopId: to }, controller.signal, routes)
      if (!controller.signal.aborted) setJourney(result)
    } catch (error) {
      if (!controller.signal.aborted) setError(error instanceof Error ? error.message : 'Could not plan this journey.')
    } finally {
      if (!controller.signal.aborted) setLoading(false)
    }
  }

  return (
    <aside className="trip-planner" hidden={hidden} aria-labelledby="planner-title">
      <h2 id="planner-title">Bring me there</h2>
      <p>Choose your starting stop and destination.</p>
      <form onSubmit={submit}>
        <label htmlFor="journey-from">From</label>
        <button id="journey-from" className="planner-station" type="button" aria-pressed={picking === 'from'}
          disabled={!stops.length} onClick={() => setPicking('from')}>
          {stops.find((stop) => stop.id === from)?.name || 'Click to choose on the map'}
        </button>
        <label htmlFor="journey-to">To</label>
        <button id="journey-to" className="planner-station" type="button" aria-pressed={picking === 'to'}
          disabled={!stops.length} onClick={() => setPicking('to')}>
          {stops.find((stop) => stop.id === to)?.name || 'Click to choose on the map'}
        </button>
        {picking && <div role="status">Click a stop dot on the map to set your {picking === 'from' ? 'starting stop' : 'destination'}.
          <button type="button" className="planner-cancel" onClick={() => setPicking(null)}>Cancel selection</button>
        </div>}
        <button type="submit" disabled={!from || !to || from === to || loading || !!picking}>{loading ? 'Planning…' : 'Find a route'}</button>
      </form>
      {!stops.length && <p role="status">Stops will appear when route data is available.</p>}
      <p className="planner-demo">Demo planner: highlighted lines are illustrative. Transfers and travel time are not calculated.</p>
      {error && <p role="alert">{error}</p>}
      {journey && <section aria-label="Journey result" aria-live="polite">
        <h3>{journey.demo ? 'Example journey' : 'Your journey'} · {journey.minutes} min</h3>
        <p>{stops.find((stop) => stop.id === from)?.name} → {stops.find((stop) => stop.id === to)?.name}</p>
        <p>Full lines shown on the map, not a validated path between your stops.</p>
        <ol>{journey.steps.map((step, index) => <li key={index}><strong className="journey-line" style={{ borderLeft: `4px solid ${step.color}` }}>{step.line}</strong><span>{step.instruction}</span></li>)}</ol>
      </section>}
    </aside>
  )
}
