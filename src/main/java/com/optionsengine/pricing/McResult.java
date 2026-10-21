package com.optionsengine.pricing;

/**
 * Monte Carlo estimate.
 *
 * @param price     discounted mean payoff
 * @param stdError  standard error of {@code price}
 * @param paths     number of simulated paths (antithetic mirrors included)
 */
public record McResult(double price, double stdError, long paths) {

    public double ciLow() {
        return price - 1.96 * stdError;
    }

    public double ciHigh() {
        return price + 1.96 * stdError;
    }
}
