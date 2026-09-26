import { LngLatBounds, Map, Popup, type MapMouseEvent } from 'maplibre-gl'
import type { BusRoute } from './getRoutes'

function addRoute(map: Map, route: BusRoute) {
  const line = `route-${route.id}`
  const stops = `${line}-stops`
  const hit = `${line}-hit`

  map.addSource(line, {
    type: 'geojson',
    data: {
      type: 'Feature',
      properties: { routeId: route.id },
      geometry: { type: 'LineString', coordinates: route.coordinates },
    },
  })
  map.addLayer({
    id: line, type: 'line', source: line,
    layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: { 'line-color': route.color, 'line-width': 5, 'line-opacity': 0.8 },
  })
  
  map.addLayer({
    id: hit, type: 'line', source: line,
    paint: { 'line-width': 22, 'line-opacity': 0 },
  })
  map.addSource(stops, {
    type: 'geojson',
    data: {
      type: 'FeatureCollection',
      features: route.stops.map((stop) => ({
        type: 'Feature',
        properties: { routeId: route.id, name: stop.name },
        geometry: { type: 'Point', coordinates: stop.coordinates },
      })),
    },
  })
  map.addLayer({
    id: stops, type: 'circle', source: stops,
    paint: {
      'circle-radius': 4, 'circle-color': '#c0bebe',
      'circle-stroke-width': 2, 'circle-stroke-color': route.color,
    },
  })

  return [hit, stops]
}

export function showRoutes(map: Map, routes: BusRoute[], fit = true) {
  if (!routes.length) return

  const layers = routes.flatMap((route) => addRoute(map, route))
  const bounds = new LngLatBounds()
  for (const route of routes) {
    route.coordinates.forEach((point) => bounds.extend(point))
    route.stops.forEach((stop) => bounds.extend(stop.coordinates))
  }
  if (fit && !bounds.isEmpty()) map.fitBounds(bounds, { padding: 50, maxZoom: 14 })

  // Create the popup once; only its text changes between a route and its stops.
  const content = document.createElement('div')
  content.className = 'route-popup-body'
  const number = document.createElement('span')
  number.className = 'route-popup-number'
  const name = document.createElement('strong')
  name.className = 'route-popup-name'
  const detail = document.createElement('p')
  detail.className = 'route-popup-detail'
  content.append(number, name, detail)
  const popup = new Popup({
    closeButton: false, offset: 16, anchor: 'bottom',
    className: 'route-popup', maxWidth: 'none',
  }).setDOMContent(content)
  let active: BusRoute | undefined

  function explore(event: MapMouseEvent) {
    const feature = map.queryRenderedFeatures(event.point, { layers })[0]
    const route = routes.find((item) => item.id === feature?.properties.routeId)
    if (!route) return dismiss()

    if (active !== route) {
      if (active) map.setPaintProperty(`route-${active.id}`, 'line-width', 5)
      map.setPaintProperty(`route-${route.id}`, 'line-width', 8)
      active = route
    }
    map.getCanvas().style.cursor = 'pointer'
    content.style.setProperty('--route-color', route.color)
    number.textContent = route.number
    name.textContent = route.name
    detail.textContent = feature?.properties.name || `${route.stops.length} stops · Sample route`
    popup.setLngLat(event.lngLat).addTo(map)
  }

  function dismiss() {
    if (active) map.setPaintProperty(`route-${active.id}`, 'line-width', 5)
    active = undefined
    map.getCanvas().style.cursor = ''
    popup.remove()
  }
  map.on('mousemove', explore)
  map.on('click', explore)
  map.on('mouseout', dismiss)
  return () => {
    map.off('mousemove', explore)
    map.off('click', explore)
    map.off('mouseout', dismiss)
    popup.remove()
  }
}
