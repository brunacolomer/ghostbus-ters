import { useEffect, useMemo, useRef, useState, type FormEvent, type RefObject } from 'react'
import { Map as TransitMap, LngLatBounds, Marker, Popup, type MapMouseEvent } from 'maplibre-gl'
import type { BusRoute } from '../routes/getRoutes'
import { planJourney, type Journey } from '../routes/planJourney'
import './TripPlanner.css'

export function TripPlanner({ routes, mapRef }: { routes: BusRoute[]; mapRef: RefObject<TransitMap | null> }) {
  const stops = useMemo(() => [...new Map(routes.flatMap((route) => route.stops.map((stop) => ({ ...stop, color: route.color }))).map((stop) => [stop.id, stop])).values()]
    .sort((a, b) => a.name.localeCompare(b.name)), [routes])
  const [picking, setPicking] = useState<'from' | 'to' | null>(null)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [journey, setJourney] = useState<Journey | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const fittedJourney = useRef<Journey | null>(null)
  const pending = useRef<AbortController | null>(null)
  useEffect(() => () => pending.current?.abort(), [])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    function clearOnEmptyClick(event: MapMouseEvent) {
      const target = event.originalEvent.target
      if (target instanceof Element && target.closest('.maplibregl-marker, .maplibregl-popup')) return
      const layers = ['planner-stops', 'bus-stops', 'bus-hit', 'live-buses'].filter((id) => map!.getLayer(id))
      if (layers.length && map!.queryRenderedFeatures(
        [[event.point.x - 5, event.point.y - 5], [event.point.x + 5, event.point.y + 5]], { layers }).length) return
      pending.current?.abort()
      setPicking(null)
      setFrom('')
      setTo('')
      setJourney(null)
      setLoading(false)
      setError('')
    }
    map.on('click', clearOnEmptyClick)
    return () => { map.off('click', clearOnEmptyClick) }
  }, [mapRef, stops])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const markers = [from, to].flatMap((id, index) => {
      const stop = stops.find((item) => item.id === id)
      if (!stop) return []
      return [new Marker({ color: index === 0 ? '#38b88c' : '#f59e0b' })
        .setLngLat(stop.coordinates).setPopup(new Popup({ className: 'planner-popup' }).setText(`${index === 0 ? 'From' : 'To'}: ${stop.name}`)).addTo(map)]
    })
    return () => markers.forEach((marker) => marker.remove())
  }, [from, to, stops, mapRef])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !picking) return
    let removed = false
    const onRemove = () => { removed = true }
    map.on('remove', onRemove)
    const popup = new Popup({ closeButton: false, offset: 12, className: 'planner-hover-popup', closeOnClick: false })
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
    let frame: number | undefined
    let lastName = ''
    function explore(event: MapMouseEvent) {
      const target = event.originalEvent.target
      if (target instanceof Element && target.closest('.maplibregl-marker, .maplibregl-popup')) return
      if (!map!.getLayer('planner-stops')) return
      const feature = map!.queryRenderedFeatures([[event.point.x - 5, event.point.y - 5], [event.point.x + 5, event.point.y + 5]], { layers: ['planner-stops'] })[0]
      map!.getCanvas().style.cursor = feature ? 'pointer' : 'crosshair'
      if (!feature) { popup.remove(); return }
      if (lastName !== feature.properties.name) {
        lastName = feature.properties.name
        popup.setText(lastName)
      }
      popup.setLngLat(event.lngLat)
      if (!popup.isOpen()) popup.addTo(map!)
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
    function move(event: MapMouseEvent) {
      if (frame !== undefined) cancelAnimationFrame(frame)
      frame = requestAnimationFrame(() => { frame = undefined; explore(event) })
    }
    function leave() {
      if (frame !== undefined) cancelAnimationFrame(frame)
      frame = undefined
      popup.remove()
    }
    if (map.isStyleLoaded()) addStops()
    map.on('style.load', addStops)
    map.on('mousemove', move)
    map.on('mouseout', leave)
    map.on('click', explore)
    return () => {
      map.off('style.load', addStops)
      map.off('mousemove', move)
      map.off('mouseout', leave)
      leave()
      map.off('click', explore)
      popup.remove()
      map.getCanvas().style.cursor = ''
      map.off('remove', onRemove)
      if (removed) return
      if (map.getLayer('planner-stops')) map.removeLayer('planner-stops')
      if (map.getSource('planner-stops')) map.removeSource('planner-stops')
    }
  }, [picking, stops, from, to, mapRef])

  useEffect(() => {
    const map = mapRef.current
    if (!map || picking || !journey) return
    let removed = false
    const onRemove = () => { removed = true }
    map.on('remove', onRemove)
    const previousOpacity = map.getLayer('bus-lines') ? map.getPaintProperty('bus-lines', 'line-opacity') : 0.5
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
      if (map!.getLayer('bus-lines') && map!.getPaintProperty('bus-lines', 'line-opacity') !== 0.12) map!.setPaintProperty('bus-lines', 'line-opacity', 0.08)
      if (fittedJourney.current !== journey) {
        const bounds = new LngLatBounds()
        journey!.routes.forEach((route) => route.coordinates.forEach((point) => bounds.extend(point)))
        if (!bounds.isEmpty()) map!.fitBounds(bounds, { padding: window.innerWidth > 800
          ? { left: 420, right: 70, top: 180, bottom: 80 } : 60, maxZoom: 13 })
        fittedJourney.current = journey
      }
    }
    if (map.isStyleLoaded()) draw()
    map.on('style.load', draw)
    return () => {
      map.off('style.load', draw)
      map.off('remove', onRemove)
      if (removed) return
      if (map.getLayer('planner-journey')) map.removeLayer('planner-journey')
      if (map.getSource('planner-journey')) map.removeSource('planner-journey')
      if (map.getLayer('bus-lines') && map.getPaintProperty('bus-lines', 'line-opacity') === 0.08) {
        map.setPaintProperty('bus-lines', 'line-opacity', previousOpacity)
      }
    }
  }, [journey, picking, mapRef])

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
    <aside className="trip-planner" aria-labelledby="planner-title">
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
      <p className="planner-demo">Routes use scheduled stop connections, not live departure times. Nearby transfers may require walking.</p>
      {error && <p role="alert">{error}</p>}
      {journey && <section aria-label="Journey result" aria-live="polite">
        <h3>Your journey</h3>
        <p>{stops.find((stop) => stop.id === from)?.name} → {stops.find((stop) => stop.id === to)?.name}</p>
        <p>Overall reliability score: {Math.round(journey.reliability * 100)}%. Missing observations use a 50% default.</p>
        {!journey.steps.length && <p>The destination is within walking distance (250 m).</p>}
        <ol>{journey.steps.map((step, index) => <li key={index}><strong className="journey-line" style={{ borderLeft: `4px solid ${step.color}` }}>{step.line}</strong><span>{step.instruction}</span></li>)}</ol>
      </section>}
    </aside>
  )
}
