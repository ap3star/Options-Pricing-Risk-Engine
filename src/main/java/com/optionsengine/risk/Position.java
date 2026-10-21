package com.optionsengine.risk;

/** A holding that can be revalued as a function of its underlying's spot price. */
public sealed interface Position permits StockPosition, OptionPosition {

    String symbol();

    double quantity();

    /** Current underlying spot. */
    double spot();

    /** Position market value if the underlying were trading at {@code underlyingSpot}. */
    double value(double underlyingSpot);
}
