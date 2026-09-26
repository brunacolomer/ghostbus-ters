# Ghost Bus-ters

Minimal React + Vite + TypeScript app displaying a full-screen Miami map.

```sh
npm install
npm run dev
```

The map uses MapLibre GL JS and OpenFreeMap's free Positron style. No API key is needed. Internet access and WebGL are required.

- `src/App.tsx`: renders the map.
- `src/components/MapView.tsx`: map setup, Vite worker configuration, and cleanup.
- `src/index.css`: full-screen sizing.

Initial camera: longitude -80.1918, latitude 25.7617, zoom 11.7.

Run `npm run build` and `npm run lint` to check the project.
