package com.optionsengine.model;

/**
 * Option sensitivities. Vega and rho are per 1.00 (100 percentage points) change;
 * theta is per year (calendar decay, negative for long options in the usual case).
 */
public record Greeks(double delta, double gamma, double vega, double theta, double rho) {
}
