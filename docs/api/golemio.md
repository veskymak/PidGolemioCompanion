# Golemio API

The app uses the Golemio API at `https://api.golemio.cz/`. The Retrofit interface is in `app/src/main/java/cz/pid/golemiocompanion/data/remote/api/GolemioApiService.kt`; requests are mapped into app models by `GolemioRepositoryImpl.kt`.

Provider reference: [Golemio API documentation](https://api.golemio.cz/docs/openapi/).

## Authentication and configuration

Requests send the API token in the `X-Access-Token` header. The app can take the token from its in-app laboratory settings or from build configuration (`GOLEMIO_API_KEY` in `local.properties`, a Gradle project property, or the `GOLEMIO_API_KEY` environment variable). Keep personal tokens out of source control.

## Endpoints used

All listed endpoints use `GET`.

| Endpoint | Purpose and app behavior |
| --- | --- |
| `/v2/pid/departureboards` | Departures for a stop. The repository tries an ASW node first, then candidate stop names, then a CIS station ID as a fallback. The service supports `names`, `aswIds`, `cisIds`, and `ids`; requests use `minutesBefore=0`, `minutesAfter=240`, and `limit=50`. Defaults also include `preferredTimezone=Europe/Prague`, `mode=departures`, and `order=real`. |
| `/v2/vehiclepositions` | Live vehicle positions. The repository requests up to 5,000 features, sets `includeNotTracking=false`, and optionally sends `routeShortName`. GeoJSON coordinates are longitude, latitude. |
| `/v3/parking` | Preferred parking-lot data. Requests use `primarySource=tsk-offstreet` and `limit=100`. |
| `/v3/parking-measurements` | Live capacity and availability measurements for the returned v3 lots. Requested with `primarySource=tsk-offstreet` and `limit=100`. |
| `/v2/parking` | Fallback parking data if the v3 lots response contains no features. The service's default limit is 100. |
| `/v2/sharedbikes` | Shared-bike locations, with the service's default limit of 150. |

The service interface contains request defaults; repository call sites may override them. When changing a request or DTO, check both `GolemioApiService.kt` and `app/src/main/java/cz/pid/golemiocompanion/data/remote/dto/GolemioDtos.kt`.

## Related configuration not currently called

The Gradle configuration defines `PID_IG_BASE_URL`, but the app's current Retrofit service does not call the PID Input Gateway. The `MAPS_API_KEY` build value and Google Maps Compose dependencies are also not used by the current map screens; those screens use osmdroid. Treat these as configured dependencies/values, not active API integrations.
