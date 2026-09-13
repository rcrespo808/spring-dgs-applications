# MarketGraph

Crypto markets, currency relationships, and custom basket indices through GraphQL. Java 21, Spring Boot, Netflix DGS, PostgreSQL, and an interactive browser workspace.

[Live workspace](https://rcrespo808.github.io/spring-dgs-markets/) | [Application manual](docs/APPLICATION_MANUAL.md)

## Market data

The Binance spot panel streams BTC/USDT, ETH/USDT, and SOL/USDT over the public market-data WebSocket. REST provides an initial snapshot and a fallback when the stream is unavailable. Prices, rolling 24-hour changes, receipt timestamps, and session sparklines update in the browser. No credentials are required.

`binancePrices` exposes the live quotes through GraphQL. The Java service fetches Binance REST snapshots with a five-second cache; the browser uses its streaming cache. Unavailable or stale data produces `FEED_UNAVAILABLE`.

The **Reference lab** contains separate, editable synthetic USD values for BTC, ETH, SOL, USD, EUR, and JPY. It supports cross rates, conversions, and custom indices. Binance quotes are denominated in USDT; they are never silently treated as USD or mixed into the synthetic reference model.

## Run

```sh
docker compose up --build -d
```

Open [GraphiQL](http://localhost:8080/graphiql). Compose runs PostgreSQL with a persistent `markets-data` volume. Set `PORT=8081` before the Compose command if needed.

With Java 21 and Maven 3.9+:

```sh
mvn verify
mvn spring-boot:run
```

The default H2 database is in memory. The API is at `POST /graphql`; health is at `GET /actuator/health`.

## Explore

```graphql
query {
  binancePrices { symbol price quoteAsset change24h observedAt source }
  pairs { id base { symbol kind } quote { symbol } referenceRate }
  convert(from: "ETH", to: "BTC", amount: "2") { rate result }
  indices { id name level valueUsd constituents { asset { symbol } quantity } }
}
```

The synthetic ETH/BTC rate starts at `0.050000000000`; converting two ETH returns `0.100000000000` BTC. Both custom indices start at 100 points. Increasing the BTC reference from 60,000 to 66,000 moves Crypto Duo to 105 points.

```graphql
mutation {
  setReferencePrice(symbol: "BTC", usdReference: "66000", expectedVersion: 0) {
    symbol usdReference version
  }
}
```

## Design

| Concern | Implementation |
| --- | --- |
| Schema contract | SDL shared between DGS and browser GraphQL.js |
| Currency relationships | Base/quote assets and derived reference rates |
| Decimal arithmetic | Java BigDecimal and browser decimal.js; monetary values travel as strings |
| Batching | Shared asset DataLoader for pair sides and constituent assets; separate constituent batch loader |
| Indices | Fixed-quantity baskets, USD valuation, fixed divisor, normalized level |
| Consistency | Transactional writes, unique pair constraint, optimistic price versions |
| Persistence | Parameterized JDBC queries, Flyway migrations, PostgreSQL/H2 |
| Live feed | Public Binance WebSocket, reconnect backoff, REST fallback, freshness checks |

## Browser development

```sh
npm ci
npm test
npm run build:demo
```

GitHub Pages serves `docs/` from `main`. Commit the generated `docs/assets/app.js` after source edits. The browser executes the shared SDL locally; its reference changes reset on reload. It does not call the Java service. The only external runtime connection is public Binance market data.

Java tests cover conversions, rounding, batching, index valuation, persistence, conflicts, request cache invalidation, and Binance cache/failure behavior. Browser tests cover matching reference calculations and feed parsing. [`docs/ci/verify.yml`](docs/ci/verify.yml) remains a workflow template; enabling it requires a GitHub credential with workflow permission.

## Operations

The service is a local reference-data tool. It has no exchange account access, order execution, authentication, or authorization. Reference edits affect only the model; they never place trades. Prices are last-trade data, not executable bids or asks. The custom indices are application-defined baskets, not exchange benchmarks.

V1 and V2 migrations are retained unchanged as historical migration records. V3 introduces the market domain without deleting existing data. Legacy tables are unused by the current API. The renamed Compose project uses a separate market database volume, preserving the previous volume.

## References

- [Netflix DGS](https://netflix.github.io/dgs/)
- [Binance public market-data endpoints](https://github.com/binance/binance-spot-api-docs/blob/master/faqs/market_data_only.md)
- [Binance WebSocket streams](https://github.com/binance/binance-spot-api-docs/blob/master/web-socket-streams.md)
