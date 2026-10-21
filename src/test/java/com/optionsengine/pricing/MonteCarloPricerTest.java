package com.optionsengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optionsengine.model.Greeks;
import com.optionsengine.model.Option;
import com.optionsengine.model.OptionType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class MonteCarloPricerTest {

    private static final MonteCarloPricer PRICER = new MonteCarloPricer(4);

    @AfterAll
    static void shutdown() {
        PRICER.close();
    }

    private static Option option(OptionType type) {
        return new Option(100, 105, 0.05, 0.02, 0.25, 1.0, type);
    }

    @Test
    void convergesToAnalyticalPriceWithinConfidenceInterval() {
        for (OptionType type : OptionType.values()) {
            Option o = option(type);
            double exact = BlackScholes.price(o);
            McResult r = PRICER.price(o, 2_000_000, 12345, true);
            assertThat(Math.abs(r.price() - exact)).as("%s abs error", type).isLessThan(4 * r.stdError());
            assertThat(r.stdError()).isLessThan(0.02);
        }
    }

    @Test
    void errorShrinksRoughlyWithSqrtOfPaths() {
        Option o = option(OptionType.CALL);
        double small = PRICER.price(o, 40_000, 1, true).stdError();
        double large = PRICER.price(o, 4_000_000, 1, true).stdError();
        // 100x more paths -> ~10x smaller standard error
        assertThat(small / large).isBetween(8.0, 12.5);
    }

    @Test
    void antitheticVariatesReduceVariance() {
        Option o = option(OptionType.CALL);
        // Same number of payoff evaluations: 2M plain paths vs 1M antithetic pairs (2M paths).
        double plain = PRICER.price(o, 2_000_000, 99, false).stdError();
        double anti = PRICER.price(o, 2_000_000, 99, true).stdError();
        assertThat(anti).isLessThan(plain);
    }

    @Test
    void resultIsReproducibleAndIndependentOfParallelism() {
        Option o = option(OptionType.PUT);
        McResult parallel = PRICER.price(o, 500_000, 2024, true);
        McResult again = PRICER.price(o, 500_000, 2024, true);
        try (MonteCarloPricer sequential = new MonteCarloPricer(1)) {
            McResult seq = sequential.price(o, 500_000, 2024, true);
            assertThat(seq).isEqualTo(parallel);
        }
        assertThat(again).isEqualTo(parallel);
        assertThat(PRICER.price(o, 500_000, 2025, true).price()).isNotEqualTo(parallel.price());
    }

    @Test
    void handlesPathCountsNotDivisibleByChunkSize() {
        Option o = option(OptionType.CALL);
        McResult r = PRICER.price(o, 100_001, 5, true);
        assertThat(r.paths()).isEqualTo(100_002);
        assertThat(r.price()).isPositive();
    }

    @Test
    void monteCarloGreeksAgreeWithAnalyticalGreeks() {
        Option o = option(OptionType.CALL);
        Greeks exact = BlackScholes.greeks(o);
        Greeks mc = PRICER.greeks(o, 4_000_000, 77, true);
        assertThat(mc.delta()).isCloseTo(exact.delta(), org.assertj.core.data.Offset.offset(0.01));
        assertThat(mc.vega()).isCloseTo(exact.vega(), org.assertj.core.data.Offset.offset(0.8));
        assertThat(mc.rho()).isCloseTo(exact.rho(), org.assertj.core.data.Offset.offset(0.8));
        assertThat(mc.gamma()).isCloseTo(exact.gamma(), org.assertj.core.data.Offset.offset(0.003));
        assertThat(mc.theta()).isCloseTo(exact.theta(), org.assertj.core.data.Offset.offset(0.8));
    }

    @Test
    void rejectsTooFewPaths() {
        assertThatThrownBy(() -> PRICER.price(option(OptionType.CALL), 1, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
