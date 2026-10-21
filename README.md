# Options Pricing & Risk Engine

Java 21 / Spring Boot pricing engine for European options.

- **Black-Scholes-Merton** closed-form price and analytical Greeks (delta, gamma, vega, theta, rho), with continuous dividend yield.
- **Monte Carlo** pricer using the exact GBM terminal solution and **antithetic variates**. Paths are generated in parallel on a `ForkJoinPool`; each fixed-size chunk is seeded from `(seed, chunkIndex)` and reduced in a fixed tree order, so results are bit-identical for any parallelism. Reports a standard error, and Greeks via central differences with common random numbers.
- **Implied volatility** via Newton-Raphson safeguarded by a bisection bracket. Where vega vanishes (deep ITM/OTM, short expiry) or a Newton step leaves the bracket, it bisects. Prices that violate no-arbitrage bounds are rejected.
- **Historical-simulation VaR / expected shortfall** with full revaluation of stock and option positions, preserving cross-asset correlation by applying each day's returns jointly.
- **REST API** (Spring Boot) and **JMH** benchmarks.

## Build and test

```bash
mvn verify
```

The tests include put-call parity and no-arbitrage bound checks on thousands of random inputs, analytical-vs-finite-difference Greeks, Monte Carlo convergence to the analytical price (within 4 standard errors, error shrinking as 1/sqrt(N)), antithetic variance reduction, parallel/sequential reproducibility, implied-vol round trips, and the bisection fallback.

## Run the API

```bash
mvn spring-boot:run
```

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/price` | Price with `BLACK_SCHOLES` (default) or `MONTE_CARLO` |
| `POST /api/v1/greeks` | Analytical or Monte Carlo Greeks |
| `POST /api/v1/implied-vol` | Solve implied volatility from a market price |
| `POST /api/v1/var` | Historical-simulation VaR and expected shortfall for a portfolio |

```bash
curl -s localhost:8080/api/v1/price -H 'Content-Type: application/json' -d '{
  "option": {"spot":100,"strike":105,"rate":0.05,"dividendYield":0.02,"volatility":0.25,"expiry":1,"type":"CALL"},
  "model": "MONTE_CARLO",
  "monteCarlo": {"paths": 1000000, "seed": 42, "antithetic": true}
}'
# {"model":"MONTE_CARLO","price":...,"stdError":...,"paths":1000000}

curl -s localhost:8080/api/v1/implied-vol -H 'Content-Type: application/json' -d '{
  "marketPrice": 10.4506, "spot":100, "strike":100, "rate":0.05, "dividendYield":0, "expiry":1, "type":"CALL"
}'

curl -s localhost:8080/api/v1/var -H 'Content-Type: application/json' -d '{
  "confidence": 0.99,
  "positions": [
    {"symbol":"AAA","kind":"STOCK","quantity":100,"spot":100},
    {"symbol":"AAA","kind":"OPTION","quantity":-5,"spot":100,"strike":105,"rate":0.03,
     "volatility":0.3,"expiry":0.5,"type":"CALL"}
  ],
  "logReturns": {"AAA": [-0.02, 0.01, 0.005, -0.015, 0.03]}
}'
```

`logReturns` are per-period log returns (e.g. daily), one series per symbol, all the same length. Each scenario shocks the spot instantaneously; option positions are repriced with Black-Scholes. `VaR` is reported as a positive loss.

Invalid input or no-arbitrage violations return `400` with `{"error": "..."}`.

## Benchmarks

```bash
scripts/run-benchmarks.sh                                   # all benchmarks
scripts/run-benchmarks.sh -p parallelism=1,4 monteCarlo     # JMH args pass through
```

Indicative short run (Apple silicon laptop, 2 warmup and 3 measured 1s iterations, so noisy), 1M paths:

| Benchmark | parallelism 1 | parallelism 4 |
| --- | --- | --- |
| `monteCarloAntithetic` | 7.8 ms | 2.1 ms |
| `blackScholesPrice` | 0.03 us | |
| `impliedVol` | 0.3 us | |

Use the default JMH settings (no flags) for numbers worth quoting.

## Layout

```
model/    Option, OptionType, Greeks
pricing/  BlackScholes, MonteCarloPricer, ImpliedVolSolver, NormalDistribution
risk/     Position (stock/option), HistoricalVar
api/      REST controller, DTOs, error handling
```
