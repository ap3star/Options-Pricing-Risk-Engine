package com.optionsengine.model;

public enum OptionType {
    CALL, PUT;

    /** Undiscounted payoff at expiry for the given terminal spot and strike. */
    public double payoff(double spot, double strike) {
        return this == CALL ? Math.max(spot - strike, 0.0) : Math.max(strike - spot, 0.0);
    }
}
