package com.optionsengine.risk;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Historical-simulation Value-at-Risk with full revaluation.
 *
 * <p>Each historical scenario i applies the i-th observed log return of every underlying to its current
 * spot (preserving cross-asset correlation), reprices the whole portfolio, and records the P&amp;L. VaR is the
 * empirical loss quantile; expected shortfall is the mean of losses at or beyond it. Returns are applied
 * as instantaneous shocks over whatever horizon the return series represents (e.g. daily).
 */
public final class HistoricalVar {

    private HistoricalVar() {
    }

    public static VarResult compute(List<Position> portfolio, Map<String, double[]> logReturns, double confidence) {
        if (portfolio.isEmpty()) {
            throw new IllegalArgumentException("portfolio must not be empty");
        }
        if (!(confidence > 0.0 && confidence < 1.0)) {
            throw new IllegalArgumentException("confidence must be in (0, 1)");
        }
        int n = -1;
        for (Position p : portfolio) {
            double[] series = logReturns.get(p.symbol());
            if (series == null || series.length == 0) {
                throw new IllegalArgumentException("no return history for symbol " + p.symbol());
            }
            if (n >= 0 && series.length != n) {
                throw new IllegalArgumentException("return series must have equal length across symbols");
            }
            n = series.length;
        }
        final int scenarios = n;

        double current = portfolio.stream().mapToDouble(p -> p.value(p.spot())).sum();
        double[] losses = new double[scenarios];
        IntStream.range(0, scenarios).parallel().forEach(i -> {
            double stressed = 0.0;
            for (Position p : portfolio) {
                stressed += p.value(p.spot() * Math.exp(logReturns.get(p.symbol())[i]));
            }
            losses[i] = current - stressed;
        });

        Arrays.sort(losses);
        int idx = Math.max(0, (int) Math.ceil(confidence * scenarios) - 1);
        double var = losses[idx];
        double tail = 0.0;
        for (int i = idx; i < scenarios; i++) {
            tail += losses[i];
        }
        return new VarResult(confidence, var, tail / (scenarios - idx), scenarios, current);
    }
}
