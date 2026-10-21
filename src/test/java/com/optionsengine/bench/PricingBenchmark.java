package com.optionsengine.bench;

import com.optionsengine.model.Option;
import com.optionsengine.model.OptionType;
import com.optionsengine.pricing.BlackScholes;
import com.optionsengine.pricing.ImpliedVolSolver;
import com.optionsengine.pricing.MonteCarloPricer;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Run with {@code scripts/run-benchmarks.sh}. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PricingBenchmark {

    @Param({"1", "2", "4", "8"})
    public int parallelism;

    @Param({"1000000"})
    public long paths;

    private Option option;
    private MonteCarloPricer pricer;
    private double marketPrice;

    @Setup
    public void setup() {
        option = new Option(100, 105, 0.05, 0.02, 0.25, 1.0, OptionType.CALL);
        pricer = new MonteCarloPricer(parallelism);
        marketPrice = BlackScholes.price(option.withVolatility(0.31));
    }

    @TearDown
    public void tearDown() {
        pricer.close();
    }

    @Benchmark
    public double blackScholesPrice() {
        return BlackScholes.price(option);
    }

    @Benchmark
    public void blackScholesGreeks(Blackhole bh) {
        bh.consume(BlackScholes.greeks(option));
    }

    @Benchmark
    public double impliedVol() {
        return ImpliedVolSolver.solve(marketPrice, 100, 105, 0.05, 0.02, 1.0, OptionType.CALL).volatility();
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public double monteCarloAntithetic() {
        return pricer.price(option, paths, 42, true).price();
    }
}
