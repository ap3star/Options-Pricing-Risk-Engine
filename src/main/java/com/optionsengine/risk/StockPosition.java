package com.optionsengine.risk;

public record StockPosition(String symbol, double quantity, double spot) implements Position {

    @Override
    public double value(double underlyingSpot) {
        return quantity * underlyingSpot;
    }
}
