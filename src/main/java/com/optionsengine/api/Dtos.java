package com.optionsengine.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.optionsengine.model.Greeks;
import com.optionsengine.model.Option;
import com.optionsengine.model.OptionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;

/** Request and response payloads for the REST API. */
public final class Dtos {

    private Dtos() {
    }

    public enum Model { BLACK_SCHOLES, MONTE_CARLO }

    public record OptionRequest(
            @Positive double spot,
            @Positive double strike,
            double rate,
            double dividendYield,
            @Positive double volatility,
            @Positive double expiry,
            @NotNull OptionType type) {

        Option toOption() {
            return new Option(spot, strike, rate, dividendYield, volatility, expiry, type);
        }
    }

    public record McSettings(
            @Min(1_000) @Max(100_000_000) Long paths,
            Long seed,
            Boolean antithetic) {

        long pathsOrDefault() {
            return paths == null ? 1_000_000L : paths;
        }

        long seedOrDefault() {
            return seed == null ? 42L : seed;
        }

        boolean antitheticOrDefault() {
            return antithetic == null || antithetic;
        }
    }

    public record PriceRequest(
            @NotNull @Valid OptionRequest option,
            Model model,
            @Valid McSettings monteCarlo) {

        Model modelOrDefault() {
            return model == null ? Model.BLACK_SCHOLES : model;
        }

        McSettings mcOrDefault() {
            return monteCarlo == null ? new McSettings(null, null, null) : monteCarlo;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PriceResponse(Model model, double price, Double stdError, Long paths) {
    }

    public record GreeksResponse(Model model, Greeks greeks) {
    }

    public record ImpliedVolRequest(
            @Positive double marketPrice,
            @Positive double spot,
            @Positive double strike,
            double rate,
            double dividendYield,
            @Positive double expiry,
            @NotNull OptionType type) {
    }

    public record ImpliedVolResponse(double impliedVolatility, int iterations, int bisectionSteps) {
    }

    public enum PositionKind { STOCK, OPTION }

    /** Stock positions use {@code spot}; option positions additionally need the option fields. */
    public record PositionRequest(
            @NotBlank String symbol,
            @NotNull PositionKind kind,
            double quantity,
            @Positive double spot,
            Double strike,
            Double rate,
            Double dividendYield,
            Double volatility,
            Double expiry,
            OptionType type) {
    }

    public record VarRequest(
            @NotEmpty List<@Valid PositionRequest> positions,
            @NotEmpty Map<String, double[]> logReturns,
            @DecimalMin("0.5") @DecimalMax("0.9999") double confidence) {
    }
}
