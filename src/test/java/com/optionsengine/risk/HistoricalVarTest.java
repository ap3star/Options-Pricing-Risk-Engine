package com.optionsengine.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optionsengine.model.Option;
import com.optionsengine.model.OptionType;
import com.optionsengine.pricing.BlackScholes;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class HistoricalVarTest {

    private static double[] returns(long seed, int n, double vol) {
        SplittableRandom rnd = new SplittableRandom(seed);
        double[] r = new double[n];
        for (int i = 0; i < n; i++) {
            r[i] = vol * rnd.nextGaussian();
        }
        return r;
    }

    @Test
    void longStockVarIsTheEmpiricalLossQuantile() {
        // Returns -10%, -5%, 0, +5%, +10% ... 100 scenarios linearly spaced
        double[] r = new double[100];
        for (int i = 0; i < 100; i++) {
            r[i] = -0.10 + 0.20 * i / 99.0;
        }
        var stock = new StockPosition("AAA", 100, 50.0);
        VarResult res = HistoricalVar.compute(List.of(stock), Map.of("AAA", r), 0.95);

        double[] losses = new double[100];
        for (int i = 0; i < 100; i++) {
            losses[i] = 5000 - 5000 * Math.exp(r[i]);
        }
        java.util.Arrays.sort(losses);
        assertThat(res.valueAtRisk()).isEqualTo(losses[94]);
        assertThat(res.expectedShortfall()).isGreaterThanOrEqualTo(res.valueAtRisk());
        assertThat(res.portfolioValue()).isEqualTo(5000.0);
    }

    @Test
    void varGrowsWithConfidenceAndExpectedShortfallExceedsVar() {
        var stock = new StockPosition("AAA", 1000, 100);
        Map<String, double[]> rets = Map.of("AAA", returns(1, 1000, 0.02));
        VarResult v90 = HistoricalVar.compute(List.of(stock), rets, 0.90);
        VarResult v99 = HistoricalVar.compute(List.of(stock), rets, 0.99);
        assertThat(v99.valueAtRisk()).isGreaterThan(v90.valueAtRisk());
        assertThat(v99.expectedShortfall()).isGreaterThanOrEqualTo(v99.valueAtRisk());
        // ~2.33 sigma * 2% * 100k notional
        assertThat(v99.valueAtRisk()).isBetween(3500.0, 5800.0);
    }

    @Test
    void longOptionLossIsBoundedByPremiumPaid() {
        Option call = new Option(100, 100, 0.03, 0, 0.3, 0.5, OptionType.CALL);
        double premium = BlackScholes.price(call);
        var pos = new OptionPosition("AAA", 10, call);
        VarResult res = HistoricalVar.compute(List.of(pos), Map.of("AAA", returns(2, 750, 0.05)), 0.99);
        assertThat(res.valueAtRisk()).isPositive().isLessThanOrEqualTo(10 * premium);
    }

    @Test
    void deltaHedgedPortfolioHasLessVarThanNakedOption() {
        Option call = new Option(100, 100, 0.03, 0, 0.3, 0.5, OptionType.CALL);
        double delta = BlackScholes.greeks(call).delta();
        Map<String, double[]> rets = Map.of("AAA", returns(3, 1000, 0.01));
        VarResult naked = HistoricalVar.compute(List.of(new OptionPosition("AAA", 100, call)), rets, 0.99);
        VarResult hedged = HistoricalVar.compute(List.of(
                new OptionPosition("AAA", 100, call),
                new StockPosition("AAA", -100 * delta, 100)), rets, 0.99);
        assertThat(hedged.valueAtRisk()).isLessThan(naked.valueAtRisk());
    }

    @Test
    void shortOptionCanLoseMoreThanLongPremium() {
        Option put = new Option(100, 100, 0.03, 0, 0.3, 0.5, OptionType.PUT);
        Map<String, double[]> rets = Map.of("AAA", returns(4, 1000, 0.04));
        VarResult longPut = HistoricalVar.compute(List.of(new OptionPosition("AAA", 1, put)), rets, 0.99);
        VarResult shortPut = HistoricalVar.compute(List.of(new OptionPosition("AAA", -1, put)), rets, 0.99);
        assertThat(shortPut.valueAtRisk()).isGreaterThan(longPut.valueAtRisk());
    }

    @Test
    void validatesInput() {
        var stock = new StockPosition("AAA", 1, 100);
        assertThatThrownBy(() -> HistoricalVar.compute(List.of(), Map.of(), 0.99))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HistoricalVar.compute(List.of(stock), Map.of(), 0.99))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("AAA");
        assertThatThrownBy(() -> HistoricalVar.compute(List.of(stock), Map.of("AAA", new double[]{0.01}), 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HistoricalVar.compute(
                List.of(stock, new StockPosition("BBB", 1, 10)),
                Map.of("AAA", new double[]{0.01, 0.02}, "BBB", new double[]{0.01}), 0.9))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("equal length");
    }
}
