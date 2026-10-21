package com.optionsengine.pricing;

/** Standard normal density and cumulative distribution (Hart/West double-precision algorithm, ~1e-15). */
public final class NormalDistribution {

    private static final double INV_SQRT_2PI = 0.3989422804014327;

    private NormalDistribution() {
    }

    public static double pdf(double x) {
        return INV_SQRT_2PI * Math.exp(-0.5 * x * x);
    }

    public static double cdf(double x) {
        double xAbs = Math.abs(x);
        double c;
        if (xAbs > 37.0) {
            c = 0.0;
        } else {
            double e = Math.exp(-0.5 * xAbs * xAbs);
            if (xAbs < 7.07106781186547) {
                double b = 3.52624965998911e-02 * xAbs + 0.700383064443688;
                b = b * xAbs + 6.37396220353165;
                b = b * xAbs + 33.912866078383;
                b = b * xAbs + 112.079291497871;
                b = b * xAbs + 221.213596169931;
                b = b * xAbs + 220.206867912376;
                c = e * b;
                b = 8.83883476483184e-02 * xAbs + 1.75566716318264;
                b = b * xAbs + 16.064177579207;
                b = b * xAbs + 86.7807322029461;
                b = b * xAbs + 296.564248779674;
                b = b * xAbs + 637.333633378831;
                b = b * xAbs + 793.826512519948;
                b = b * xAbs + 440.413735824752;
                c = c / b;
            } else {
                double b = xAbs + 0.65;
                b = xAbs + 4.0 / b;
                b = xAbs + 3.0 / b;
                b = xAbs + 2.0 / b;
                b = xAbs + 1.0 / b;
                c = e / b / 2.506628274631;
            }
        }
        return x > 0.0 ? 1.0 - c : c;
    }
}
