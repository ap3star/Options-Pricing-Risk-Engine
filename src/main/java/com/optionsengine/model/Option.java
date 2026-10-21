package com.optionsengine.model;

/**
 * European option on an asset paying a continuous dividend yield.
 *
 * @param spot          current underlying price (&gt; 0)
 * @param strike        strike price (&gt; 0)
 * @param rate          continuously compounded risk-free rate
 * @param dividendYield continuous dividend yield
 * @param volatility    annualised volatility (&gt; 0)
 * @param expiry        time to expiry in years (&gt; 0)
 * @param type          call or put
 */
public record Option(double spot, double strike, double rate, double dividendYield,
                     double volatility, double expiry, OptionType type) {

    public Option {
        requirePositive(spot, "spot");
        requirePositive(strike, "strike");
        requirePositive(volatility, "volatility");
        requirePositive(expiry, "expiry");
        requireFinite(rate, "rate");
        requireFinite(dividendYield, "dividendYield");
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
    }

    public Option withSpot(double s) {
        return new Option(s, strike, rate, dividendYield, volatility, expiry, type);
    }

    public Option withVolatility(double v) {
        return new Option(spot, strike, rate, dividendYield, v, expiry, type);
    }

    public Option withRate(double r) {
        return new Option(spot, strike, r, dividendYield, volatility, expiry, type);
    }

    public Option withExpiry(double t) {
        return new Option(spot, strike, rate, dividendYield, volatility, t, type);
    }

    private static void requirePositive(double v, String name) {
        if (!(v > 0.0) || Double.isInfinite(v)) {
            throw new IllegalArgumentException(name + " must be positive and finite, got " + v);
        }
    }

    private static void requireFinite(double v, String name) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException(name + " must be finite, got " + v);
        }
    }
}
