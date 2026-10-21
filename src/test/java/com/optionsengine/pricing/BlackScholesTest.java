package com.optionsengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optionsengine.model.Greeks;
import com.optionsengine.model.Option;
import com.optionsengine.model.OptionType;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BlackScholesTest {

    private static Option atm(OptionType type) {
        return new Option(100, 100, 0.05, 0.0, 0.2, 1.0, type);
    }

    @Test
    void normalCdfMatchesReferenceValues() {
        assertThat(NormalDistribution.cdf(0)).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-15));
        assertThat(NormalDistribution.cdf(1.96)).isCloseTo(0.9750021048517795, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(NormalDistribution.cdf(-1.0)).isCloseTo(0.15865525393145707, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(NormalDistribution.cdf(-8.0)).isCloseTo(6.220960574271786e-16, org.assertj.core.data.Offset.offset(1e-20));
        assertThat(NormalDistribution.cdf(40)).isEqualTo(1.0);
    }

    @Test
    void matchesTextbookPrices() {
        // S=K=100, r=5%, sigma=20%, T=1 (Hull)
        assertThat(BlackScholes.price(atm(OptionType.CALL))).isCloseTo(10.450583572185565, org.assertj.core.data.Offset.offset(1e-8));
        assertThat(BlackScholes.price(atm(OptionType.PUT))).isCloseTo(5.573526022256971, org.assertj.core.data.Offset.offset(1e-8));
    }

    @Test
    void putCallParityHoldsAcrossRandomInputs() {
        SplittableRandom rnd = new SplittableRandom(7);
        for (int i = 0; i < 2000; i++) {
            double s = 20 + rnd.nextDouble() * 180;
            double k = 20 + rnd.nextDouble() * 180;
            double r = rnd.nextDouble() * 0.1 - 0.01;
            double q = rnd.nextDouble() * 0.05;
            double sigma = 0.05 + rnd.nextDouble() * 0.9;
            double t = 0.05 + rnd.nextDouble() * 3;
            double call = BlackScholes.price(new Option(s, k, r, q, sigma, t, OptionType.CALL));
            double put = BlackScholes.price(new Option(s, k, r, q, sigma, t, OptionType.PUT));
            double parity = s * Math.exp(-q * t) - k * Math.exp(-r * t);
            assertThat(call - put).isCloseTo(parity, org.assertj.core.data.Offset.offset(1e-9 * Math.max(1, s)));
        }
    }

    @Test
    void pricesRespectNoArbitrageBounds() {
        SplittableRandom rnd = new SplittableRandom(11);
        for (int i = 0; i < 2000; i++) {
            double s = 20 + rnd.nextDouble() * 180;
            double k = 20 + rnd.nextDouble() * 180;
            double r = rnd.nextDouble() * 0.1;
            double q = rnd.nextDouble() * 0.05;
            double sigma = 0.05 + rnd.nextDouble() * 0.9;
            double t = 0.05 + rnd.nextDouble() * 3;
            double fs = s * Math.exp(-q * t);
            double dk = k * Math.exp(-r * t);
            double call = BlackScholes.price(new Option(s, k, r, q, sigma, t, OptionType.CALL));
            double put = BlackScholes.price(new Option(s, k, r, q, sigma, t, OptionType.PUT));
            assertThat(call).isBetween(Math.max(fs - dk, 0) - 1e-9, fs + 1e-9);
            assertThat(put).isBetween(Math.max(dk - fs, 0) - 1e-9, dk + 1e-9);
        }
    }

    @Test
    void analyticalGreeksMatchFiniteDifferences() {
        for (OptionType type : OptionType.values()) {
            Option o = new Option(105, 100, 0.03, 0.01, 0.25, 0.75, type);
            Greeks g = BlackScholes.greeks(o);
            double h = 1e-4;
            double delta = (BlackScholes.price(o.withSpot(o.spot() + h)) - BlackScholes.price(o.withSpot(o.spot() - h))) / (2 * h);
            double gamma = (BlackScholes.price(o.withSpot(o.spot() + h)) - 2 * BlackScholes.price(o)
                    + BlackScholes.price(o.withSpot(o.spot() - h))) / (h * h);
            double vega = (BlackScholes.price(o.withVolatility(o.volatility() + h))
                    - BlackScholes.price(o.withVolatility(o.volatility() - h))) / (2 * h);
            double rho = (BlackScholes.price(o.withRate(o.rate() + h)) - BlackScholes.price(o.withRate(o.rate() - h))) / (2 * h);
            double theta = -(BlackScholes.price(o.withExpiry(o.expiry() + h)) - BlackScholes.price(o.withExpiry(o.expiry() - h))) / (2 * h);

            assertThat(g.delta()).isCloseTo(delta, org.assertj.core.data.Offset.offset(1e-6));
            assertThat(g.gamma()).isCloseTo(gamma, org.assertj.core.data.Offset.offset(1e-5));
            assertThat(g.vega()).isCloseTo(vega, org.assertj.core.data.Offset.offset(1e-5));
            assertThat(g.rho()).isCloseTo(rho, org.assertj.core.data.Offset.offset(1e-5));
            assertThat(g.theta()).isCloseTo(theta, org.assertj.core.data.Offset.offset(1e-5));
        }
    }

    @Test
    void callPutDeltaDiffersByDiscountedDividend() {
        Option call = new Option(100, 95, 0.04, 0.02, 0.3, 0.5, OptionType.CALL);
        Option put = call.withSpot(100);
        put = new Option(put.spot(), put.strike(), put.rate(), put.dividendYield(), put.volatility(), put.expiry(), OptionType.PUT);
        double diff = BlackScholes.greeks(call).delta() - BlackScholes.greeks(put).delta();
        assertThat(diff).isCloseTo(Math.exp(-0.02 * 0.5), org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> new Option(-1, 100, 0.05, 0, 0.2, 1, OptionType.CALL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Option(100, 100, 0.05, 0, 0.0, 1, OptionType.CALL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Option(100, 100, 0.05, 0, 0.2, 0, OptionType.CALL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
