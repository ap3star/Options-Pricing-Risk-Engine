package com.optionsengine.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optionsengine.model.OptionType;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ImpliedVolSolverTest {

    @Test
    void recoversVolatilityAcrossRandomInputs() {
        SplittableRandom rnd = new SplittableRandom(3);
        int checked = 0;
        for (int i = 0; i < 3000; i++) {
            double s = 50 + rnd.nextDouble() * 100;
            double k = s * (0.7 + rnd.nextDouble() * 0.6);
            double r = rnd.nextDouble() * 0.08;
            double q = rnd.nextDouble() * 0.04;
            double sigma = 0.05 + rnd.nextDouble() * 1.5;
            double t = 0.1 + rnd.nextDouble() * 2;
            for (OptionType type : OptionType.values()) {
                double price = BlackScholes.price(s, k, r, q, sigma, t, type);
                double lower = type == OptionType.CALL
                        ? Math.max(s * Math.exp(-q * t) - k * Math.exp(-r * t), 0)
                        : Math.max(k * Math.exp(-r * t) - s * Math.exp(-q * t), 0);
                if (price - lower < 1e-8) {
                    continue; // price carries no usable vol information (deep ITM)
                }
                ImpliedVolResult res = ImpliedVolSolver.solve(price, s, k, r, q, t, type);
                // Compare in price space: vol is ill-conditioned where vega is tiny.
                assertThat(BlackScholes.price(s, k, r, q, res.volatility(), t, type))
                        .isCloseTo(price, org.assertj.core.data.Offset.offset(1e-9));
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(5000);
    }

    @Test
    void recoversKnownVolatilityPrecisely() {
        double price = BlackScholes.price(100, 100, 0.05, 0, 0.2, 1, OptionType.CALL);
        ImpliedVolResult res = ImpliedVolSolver.solve(price, 100, 100, 0.05, 0, 1, OptionType.CALL);
        assertThat(res.volatility()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(res.bisectionSteps()).isZero();
        assertThat(res.iterations()).isLessThan(10);
    }

    @Test
    void fallsBackToBisectionWhereVegaVanishes() {
        // Far out of the money, short expiry, starting at a vol where vega is ~0: Newton cannot move.
        double s = 100, k = 160, r = 0.01, q = 0, t = 0.05, sigma = 0.9;
        double price = BlackScholes.price(s, k, r, q, sigma, t, OptionType.CALL);
        double badStart = 0.01;
        assertThat(BlackScholes.vega(s, k, r, q, badStart, t)).isLessThan(1e-10);

        ImpliedVolResult res = ImpliedVolSolver.solve(price, s, k, r, q, t, OptionType.CALL, badStart);
        assertThat(res.bisectionSteps()).isPositive();
        assertThat(res.volatility()).isCloseTo(sigma, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void putCallParityHoldsForImpliedVol() {
        double s = 100, k = 110, r = 0.04, q = 0.01, t = 0.8, sigma = 0.35;
        double call = BlackScholes.price(s, k, r, q, sigma, t, OptionType.CALL);
        double put = call - s * Math.exp(-q * t) + k * Math.exp(-r * t); // parity-implied put
        double ivCall = ImpliedVolSolver.solve(call, s, k, r, q, t, OptionType.CALL).volatility();
        double ivPut = ImpliedVolSolver.solve(put, s, k, r, q, t, OptionType.PUT).volatility();
        assertThat(ivCall).isCloseTo(sigma, org.assertj.core.data.Offset.offset(1e-8));
        assertThat(ivPut).isCloseTo(ivCall, org.assertj.core.data.Offset.offset(1e-8));
    }

    @Test
    void rejectsPricesViolatingNoArbitrageBounds() {
        double s = 100, k = 100, r = 0.05, q = 0, t = 1;
        double discK = k * Math.exp(-r * t);
        // Call above spot, put above discounted strike, call below intrinsic, price of zero.
        assertThatThrownBy(() -> ImpliedVolSolver.solve(s + 1, s, k, r, q, t, OptionType.CALL))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("upper bound");
        assertThatThrownBy(() -> ImpliedVolSolver.solve(discK + 0.5, s, k, r, q, t, OptionType.PUT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("upper bound");
        assertThatThrownBy(() -> ImpliedVolSolver.solve(s - discK - 0.5, s, k, r, q, t, OptionType.CALL))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lower bound");
        assertThatThrownBy(() -> ImpliedVolSolver.solve(0.0, s, k, r, q, t, OptionType.CALL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void impliedVolIsMonotonicInPrice() {
        double prev = 0;
        for (double price = 4; price < 40; price += 2) {
            double iv = ImpliedVolSolver.solve(price, 100, 100, 0.03, 0, 1, OptionType.CALL).volatility();
            assertThat(iv).isGreaterThan(prev);
            prev = iv;
        }
    }
}
