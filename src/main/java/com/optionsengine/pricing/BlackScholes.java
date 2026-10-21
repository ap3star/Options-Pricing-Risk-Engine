package com.optionsengine.pricing;

import com.optionsengine.model.Greeks;
import com.optionsengine.model.Option;
import com.optionsengine.model.OptionType;

/** Black-Scholes-Merton closed-form pricing and analytical Greeks for European options. */
public final class BlackScholes {

    private BlackScholes() {
    }

    public static double price(Option o) {
        return price(o.spot(), o.strike(), o.rate(), o.dividendYield(), o.volatility(), o.expiry(), o.type());
    }

    /** Allocation-free overload for hot loops (implied vol solver, VaR revaluation, benchmarks). */
    public static double price(double s, double k, double r, double q, double sigma, double t, OptionType type) {
        double sqrtT = Math.sqrt(t);
        double d1 = d1(s, k, r, q, sigma, t);
        double d2 = d1 - sigma * sqrtT;
        double fwdS = s * Math.exp(-q * t);
        double discK = k * Math.exp(-r * t);
        return type == OptionType.CALL
                ? fwdS * NormalDistribution.cdf(d1) - discK * NormalDistribution.cdf(d2)
                : discK * NormalDistribution.cdf(-d2) - fwdS * NormalDistribution.cdf(-d1);
    }

    public static double vega(double s, double k, double r, double q, double sigma, double t) {
        double d1 = d1(s, k, r, q, sigma, t);
        return s * Math.exp(-q * t) * NormalDistribution.pdf(d1) * Math.sqrt(t);
    }

    public static Greeks greeks(Option o) {
        double s = o.spot(), k = o.strike(), r = o.rate(), q = o.dividendYield();
        double sigma = o.volatility(), t = o.expiry();
        double sqrtT = Math.sqrt(t);
        double d1 = d1(s, k, r, q, sigma, t);
        double d2 = d1 - sigma * sqrtT;
        double dq = Math.exp(-q * t);
        double dr = Math.exp(-r * t);
        double pdf1 = NormalDistribution.pdf(d1);

        double gamma = dq * pdf1 / (s * sigma * sqrtT);
        double vega = s * dq * pdf1 * sqrtT;
        double decay = -s * dq * pdf1 * sigma / (2.0 * sqrtT);

        if (o.type() == OptionType.CALL) {
            double nd1 = NormalDistribution.cdf(d1);
            double nd2 = NormalDistribution.cdf(d2);
            return new Greeks(
                    dq * nd1,
                    gamma,
                    vega,
                    decay - r * k * dr * nd2 + q * s * dq * nd1,
                    k * t * dr * nd2);
        }
        double nmd1 = NormalDistribution.cdf(-d1);
        double nmd2 = NormalDistribution.cdf(-d2);
        return new Greeks(
                -dq * nmd1,
                gamma,
                vega,
                decay + r * k * dr * nmd2 - q * s * dq * nmd1,
                -k * t * dr * nmd2);
    }

    private static double d1(double s, double k, double r, double q, double sigma, double t) {
        return (Math.log(s / k) + (r - q + 0.5 * sigma * sigma) * t) / (sigma * Math.sqrt(t));
    }
}
