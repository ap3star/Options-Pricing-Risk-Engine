package com.optionsengine.pricing;

/**
 * @param volatility        solved implied volatility
 * @param iterations        total solver iterations
 * @param bisectionSteps    iterations that used the bisection fallback instead of a Newton step
 */
public record ImpliedVolResult(double volatility, int iterations, int bisectionSteps) {
}
