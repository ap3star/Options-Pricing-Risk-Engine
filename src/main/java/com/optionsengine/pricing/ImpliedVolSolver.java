package com.optionsengine.pricing;

import com.optionsengine.model.OptionType;

/**
 * Implied volatility solver: Newton-Raphson safeguarded by a bisection bracket.
 *
 * <p>The root is kept bracketed in {@code [lo, hi]}. A Newton step is taken when vega is usable and the
 * step stays strictly inside the bracket; otherwise (vega vanishing for deep ITM/OTM or very short/long
 * expiries, or an overshooting step) the solver bisects, which always converges.
 */
public final class ImpliedVolSolver {

    static final double MIN_VOL = 1e-8;
    static final double MAX_VOL = 10.0;
    private static final double VEGA_FLOOR = 1e-10;
    private static final double PRICE_TOL = 1e-12;
    private static final int MAX_ITERATIONS = 200;

    private ImpliedVolSolver() {
    }

    public static ImpliedVolResult solve(double marketPrice, double s, double k, double r, double q,
                                         double t, OptionType type) {
        return solve(marketPrice, s, k, r, q, t, type, initialGuess(s, k, r, q, t));
    }

    /** Package-private so tests can start Newton from a point where vega vanishes. */
    static ImpliedVolResult solve(double marketPrice, double s, double k, double r, double q,
                                  double t, OptionType type, double startingVol) {
        if (!(s > 0) || !(k > 0) || !(t > 0) || !Double.isFinite(marketPrice)) {
            throw new IllegalArgumentException("spot, strike, expiry must be positive and price finite");
        }
        double fwdS = s * Math.exp(-q * t);
        double discK = k * Math.exp(-r * t);
        double lower = type == OptionType.CALL ? Math.max(fwdS - discK, 0.0) : Math.max(discK - fwdS, 0.0);
        double upper = type == OptionType.CALL ? fwdS : discK;

        if (marketPrice >= upper) {
            throw new IllegalArgumentException(
                    "price %s violates no-arbitrage upper bound %s".formatted(marketPrice, upper));
        }
        if (marketPrice <= lower) {
            throw new IllegalArgumentException(
                    "price %s is at or below the no-arbitrage lower bound %s; implied volatility is undefined"
                            .formatted(marketPrice, lower));
        }

        double lo = MIN_VOL;
        double hi = MAX_VOL;
        if (BlackScholes.price(s, k, r, q, hi, t, type) < marketPrice) {
            throw new IllegalArgumentException("implied volatility exceeds " + MAX_VOL);
        }
        if (BlackScholes.price(s, k, r, q, lo, t, type) > marketPrice) {
            return new ImpliedVolResult(lo, 0, 0);
        }

        double sigma = Math.min(Math.max(startingVol, lo), hi);
        int bisections = 0;
        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            double diff = BlackScholes.price(s, k, r, q, sigma, t, type) - marketPrice;
            if (Math.abs(diff) < PRICE_TOL) {
                return new ImpliedVolResult(sigma, i, bisections);
            }
            if (diff > 0) {
                hi = sigma;
            } else {
                lo = sigma;
            }
            if (hi - lo < 1e-15) {
                return new ImpliedVolResult(0.5 * (lo + hi), i, bisections);
            }

            double vega = BlackScholes.vega(s, k, r, q, sigma, t);
            double next = vega > VEGA_FLOOR ? sigma - diff / vega : Double.NaN;
            if (Double.isNaN(next) || next <= lo || next >= hi) {
                next = 0.5 * (lo + hi);
                bisections++;
            }
            sigma = next;
        }
        throw new ArithmeticException("implied volatility solver did not converge");
    }

    /** Manaster-Koehler seed, clamped to a sane range. */
    private static double initialGuess(double s, double k, double r, double q, double t) {
        double logMoneyness = Math.log(s / k) + (r - q) * t;
        double guess = Math.sqrt(2.0 * Math.abs(logMoneyness) / t);
        return Math.min(Math.max(guess, 0.1), 3.0);
    }
}
