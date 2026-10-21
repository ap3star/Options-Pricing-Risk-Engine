package com.optionsengine.risk;

import com.optionsengine.model.Option;
import com.optionsengine.pricing.BlackScholes;

/** Signed quantity of a European option (negative = short), revalued with Black-Scholes. */
public record OptionPosition(String symbol, double quantity, Option option) implements Position {

    @Override
    public double spot() {
        return option.spot();
    }

    @Override
    public double value(double underlyingSpot) {
        return quantity * BlackScholes.price(option.withSpot(underlyingSpot));
    }
}
