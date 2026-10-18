# Map tiles and routing

The map screens use osmdroid to render map tiles and overlays. Current integration points include `app/src/main/java/cz/pid/golemiocompanion/ui/map/LiveMapScreen.kt` and `app/src/main/java/cz/pid/golemiocompanion/ui/map/RoadRouteHelper.kt`.

## CARTO Voyager tiles

The Lítačka-style map uses raster tiles from:

```text
https://{a|b|c|d}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png?key={key}
```

The source is configured for zoom levels 1–20 and 256-pixel tiles. Its key comes from laboratory settings; the source also has an in-code fallback. Do not copy key values into documentation or commits. The tile source attribution is `© OpenStreetMap contributors, © CARTO`.

Provider references: [CARTO Basemaps](https://carto.com/basemaps/) and [CARTO Basemaps usage plans](https://dashboard.basemaps.carto.com/v1/plans).

## OpenStreetMap tiles

The classic map style uses osmdroid's `TileSourceFactory.MAPNIK` tile source. The app sets an identifying osmdroid user agent. OSM data and the public tile service have separate terms; follow the [OpenStreetMap tile usage policy](https://operations.osmfoundation.org/policies/tiles/), including attribution, caching, and usage limits.

## OSRM routing

`RoadRouteHelper` requests road geometry from the public OSRM demo service, without an API token:

```text
GET https://router.project-osrm.org/route/v1/driving/{longitude,latitude;...}
    ?overview=full&geometries=geojson
```

The helper reduces inputs to at most 25 waypoints, caches successful routes in memory, and reads the first route's GeoJSON geometry. When the request or response cannot produce a route, it falls back to the effective waypoints sent to OSRM. This is driving-route geometry for display, not a transit-routing API.

Provider reference: [OSRM HTTP API — Route service](https://project-osrm.org/docs/v5.24.0/api/#route-service).

## PID web map

The map screen can load `https://mapa.pid.cz/?lat={lat}&lng={lon}&zoom=14` in a WebView. This is an embedded website, not an API endpoint used by the app.
