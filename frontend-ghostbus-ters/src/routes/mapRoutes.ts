import { LngLatBounds, Map, Popup, type GeoJSONSource, type MapMouseEvent } from 'maplibre-gl'
import type { BusRoute } from './getRoutes'

export function showRoutes(map: Map, routes: BusRoute[], fit = true) {
  if (!routes.length) return

  const routesById = new globalThis.Map(routes.map((route) => [route.id, route]))
  map.addSource('bus-routes', {
    type: 'geojson',
    data: {
      type: 'FeatureCollection',
      features: routes.filter((route) => route.coordinates.length >= 2).map((route) => ({
        type: 'Feature',
        properties: { routeId: route.id, number: route.number, name: route.name, headsign: route.headsign, color: route.color },
        geometry: { type: 'LineString', coordinates: route.coordinates },
      })),
    },
  })
  map.addLayer({
    id: 'bus-lines', type: 'line', source: 'bus-routes',
    layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: { 'line-color': ['get', 'color'], 'line-width': 2, 'line-opacity': 0.5 },
  })
  map.addLayer({
    id: 'bus-hit', type: 'line', source: 'bus-routes',
    paint: { 'line-width': 16, 'line-opacity': 0 },
  })
  map.addLayer({
    id: 'bus-highlight', type: 'line', source: 'bus-routes',
    filter: ['in', ['get', 'routeId'], ['literal', []]],
    layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: { 'line-color': ['get', 'color'], 'line-width': 5, 'line-opacity': 1 },
  })
  map.addSource('bus-stops', {
    type: 'geojson', data: { type: 'FeatureCollection', features: [] },
  })
  map.addLayer({
    id: 'bus-stops', type: 'circle', source: 'bus-stops',
    paint: {
      'circle-radius': 3, 'circle-color': '#e5e7eb',
      'circle-stroke-width': 1.5, 'circle-stroke-color': ['get', 'color'],
    },
  })
  if (fit) {
    const bounds = new LngLatBounds()
    routes.forEach((route) => route.coordinates.forEach((point) => bounds.extend(point)))
    if (!bounds.isEmpty()) map.fitBounds(bounds, { padding: 50, maxZoom: 14 })
  }

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
  let hovered: string | undefined
  let selected: string | undefined

  function highlight() {
    if (!map.getLayer('bus-highlight')) return
    map.setFilter('bus-highlight', ['in', ['get', 'routeId'], ['literal', [selected, hovered].filter((id) => id !== undefined)]])
    map.setPaintProperty('bus-lines', 'line-opacity', selected ? 0.12 : 0.5)
  }

  function explore(event: MapMouseEvent) {
    // A theme change temporarily removes these layers.
    if (!map.getLayer('bus-hit')) return
    const features = map.queryRenderedFeatures(event.point, { layers: ['bus-stops', 'bus-highlight', 'bus-hit'] })
    const feature = features.find((item) => item.layer.id === 'bus-stops') ?? features[0]
    const route = routesById.get(feature?.properties.routeId)
    const routeId = route?.id
    if (event.type === 'click' && selected !== routeId) {
      selected = routeId
      const stops = map.getSource('bus-stops') as GeoJSONSource
      stops.setData({
        type: 'FeatureCollection',
        features: (route?.stops ?? []).map((stop) => ({
          type: 'Feature',
          properties: { routeId, stopName: stop.name, color: route?.color },
          geometry: { type: 'Point', coordinates: stop.coordinates },
        })),
      })
      highlight()
    }
    if (hovered !== routeId) {
      hovered = routeId
      highlight()
    }
    map.getCanvas().style.cursor = route ? 'pointer' : ''
    if (!route) { popup.remove(); return }
    content.style.setProperty('--route-color', route.color)
    number.textContent = route.number
    name.textContent = route.name
    detail.textContent = feature?.properties.stopName || `${route.stops.length} stops`
    popup.setLngLat(event.lngLat).addTo(map)
  }

  function dismiss() {
    hovered = undefined
    highlight()
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
    map.getCanvas().style.cursor = ''
    popup.remove()
    for (const id of ['bus-stops', 'bus-highlight', 'bus-hit', 'bus-lines']) {
      if (map.getLayer(id)) map.removeLayer(id)
    }
    for (const id of ['bus-stops', 'bus-routes']) {
      if (map.getSource(id)) map.removeSource(id)
    }
  }
}
