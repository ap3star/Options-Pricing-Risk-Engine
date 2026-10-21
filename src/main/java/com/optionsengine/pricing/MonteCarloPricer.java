package com.optionsengine.pricing;

import com.optionsengine.model.Greeks;
import com.optionsengine.model.Option;
import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Monte Carlo pricer for European options under geometric Brownian motion, using the exact
 * terminal-value solution and optional antithetic variates.
 *
 * <p>Paths are split into fixed-size chunks, each seeded deterministically from {@code (seed, chunkIndex)}
 * and reduced in a fixed tree order, so a given seed yields bit-identical results regardless of
 * the pool's parallelism or thread scheduling.
 */
public final class MonteCarloPricer implements AutoCloseable {

    static final int CHUNK_SAMPLES = 1 << 14;

    private final ForkJoinPool pool;

    public MonteCarloPricer() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public MonteCarloPricer(int parallelism) {
        this.pool = new ForkJoinPool(Math.max(1, parallelism));
    }

    /**
     * Prices the option with {@code paths} simulated paths. With antithetic variates, paths are rounded up
     * to an even number and each pair (z, -z) contributes one averaged sample.
     */
    public McResult price(Option option, long paths, long seed, boolean antithetic) {
        if (paths < 2) {
            throw new IllegalArgumentException("paths must be at least 2");
        }
        long samples = antithetic ? (paths + 1) / 2 : paths;
        int chunks = (int) ((samples + CHUNK_SAMPLES - 1) / CHUNK_SAMPLES);
        Stats total = pool.invoke(new ChunkTask(option, samples, seed, antithetic, 0, chunks));

        double mean = total.sum / total.n;
        double variance = Math.max(0.0, (total.sumSq - total.n * mean * mean) / (total.n - 1));
        return new McResult(mean, Math.sqrt(variance / total.n), antithetic ? samples * 2 : samples);
    }

    /**
     * Greeks by central finite differences with common random numbers (same seed for every bump),
     * which keeps the difference quotients low-variance.
     */
    public Greeks greeks(Option o, long paths, long seed, boolean antithetic) {
        double h = 0.01 * o.spot();
        double dv = 0.001 * Math.max(o.volatility(), 0.01);
        double dr = 1e-4;
        double dt = Math.min(1.0 / 365.0, 0.5 * o.expiry());

        double base = price(o, paths, seed, antithetic).price();
        double up = price(o.withSpot(o.spot() + h), paths, seed, antithetic).price();
        double dn = price(o.withSpot(o.spot() - h), paths, seed, antithetic).price();
        double vUp = price(o.withVolatility(o.volatility() + dv), paths, seed, antithetic).price();
        double vDn = price(o.withVolatility(Math.max(1e-6, o.volatility() - dv)), paths, seed, antithetic).price();
        double rUp = price(o.withRate(o.rate() + dr), paths, seed, antithetic).price();
        double rDn = price(o.withRate(o.rate() - dr), paths, seed, antithetic).price();
        double tDn = price(o.withExpiry(o.expiry() - dt), paths, seed, antithetic).price();

        double vegaWidth = (o.volatility() + dv) - Math.max(1e-6, o.volatility() - dv);
        return new Greeks(
                (up - dn) / (2 * h),
                (up - 2 * base + dn) / (h * h),
                (vUp - vDn) / vegaWidth,
                -(base - tDn) / dt,
                (rUp - rDn) / (2 * dr));
    }

    /**
     * Stafford variant-13 finalizer. Seeds must be mixed: SplittableRandom advances by a fixed odd gamma, so
     * raw seeds that differ by multiples of it would yield the same stream shifted by a few draws.
     */
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public void close() {
        pool.shutdown();
    }

    private record Stats(double sum, double sumSq, long n) {
        Stats merge(Stats other) {
            return new Stats(sum + other.sum, sumSq + other.sumSq, n + other.n);
        }
    }

    private static final class ChunkTask extends RecursiveTask<Stats> {
        private final Option option;
        private final long samples;
        private final long seed;
        private final boolean antithetic;
        private final int from;
        private final int to;

        ChunkTask(Option option, long samples, long seed, boolean antithetic, int from, int to) {
            this.option = option;
            this.samples = samples;
            this.seed = seed;
            this.antithetic = antithetic;
            this.from = from;
            this.to = to;
        }

        @Override
        protected Stats compute() {
            if (to - from == 1) {
                return simulateChunk();
            }
            int mid = (from + to) >>> 1;
            ChunkTask left = new ChunkTask(option, samples, seed, antithetic, from, mid);
            ChunkTask right = new ChunkTask(option, samples, seed, antithetic, mid, to);
            right.fork();
            Stats l = left.compute();
            return l.merge(right.join());
        }

        private Stats simulateChunk() {
            long start = (long) from * CHUNK_SAMPLES;
            long count = Math.min(CHUNK_SAMPLES, samples - start);
            SplittableRandom rng = new SplittableRandom(mix64(seed + from * 0x9E3779B97F4A7C15L));

            double s = option.spot();
            double k = option.strike();
            double t = option.expiry();
            double drift = (option.rate() - option.dividendYield() - 0.5 * option.volatility() * option.volatility()) * t;
            double diffusion = option.volatility() * Math.sqrt(t);
            double discount = Math.exp(-option.rate() * t);
            var type = option.type();

            double sum = 0.0;
            double sumSq = 0.0;
            for (long i = 0; i < count; i++) {
                double z = rng.nextGaussian();
                double x = discount * type.payoff(s * Math.exp(drift + diffusion * z), k);
                if (antithetic) {
                    double xm = discount * type.payoff(s * Math.exp(drift - diffusion * z), k);
                    x = 0.5 * (x + xm);
                }
                sum += x;
                sumSq += x * x;
            }
            return new Stats(sum, sumSq, count);
        }
    }
}
