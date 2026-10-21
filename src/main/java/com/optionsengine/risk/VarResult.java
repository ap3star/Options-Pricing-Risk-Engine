package com.optionsengine.risk;

/**
 * @param confidence        confidence level, e.g. 0.99
 * @param valueAtRisk       loss not exceeded with the given confidence (positive number = loss)
 * @param expectedShortfall mean loss in the tail at or beyond VaR
 * @param scenarios         number of historical scenarios used
 * @param portfolioValue    current portfolio value
 */
public record VarResult(double confidence, double valueAtRisk, double expectedShortfall,
                        int scenarios, double portfolioValue) {
}
