# Energy-Consumption-System

A household energy dashboard backed by a lightweight Java HTTP API. The dashboard shows live power, daily energy use, estimated cost, carbon savings, usage history, category breakdown, and connected devices.

## Requirements

- Java 17 or newer
- Node.js and npm (only to use the provided `npm start` / `npm test` scripts)

## Run

From the project root:

```sh
npm start
```

The server compiles the Java API and starts the dashboard at <http://localhost:8080>. Set the `PORT` environment variable to use another port. To compile without starting the server, run `npm test`.

## API

- `GET /health` reports server health.
- `GET /api/summary` returns current household summary metrics.
- `GET /api/usage?period=day|week|month` returns chart labels and kWh values.
- `GET /api/devices` returns the connected-device list.
- `GET /api/categories` returns the usage breakdown by category.
- `GET /api/readings` returns the latest seven daily readings.

The current API uses sample in-memory data so the full dashboard works without a smart-meter integration or database. Readings reset when the server restarts.
