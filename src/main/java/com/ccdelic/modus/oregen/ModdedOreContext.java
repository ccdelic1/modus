package com.ccdelic.modus.oregen;

/**
 * A thread-scoped "currently generating a managed modded ore, scale its veins by this" size
 * multiplier, set by {@code BiomeDecorationOreMixin} around a managed ore's placement and
 * read by {@code OreFeatureSizeMixin} / {@code ScatteredOreFeatureSizeMixin} when the ore's
 * vanilla ore feature actually rolls a vein.
 * <p>
 * The default {@code 1.0} means "no scaling", so every ore feature placed OUTSIDE this
 * bracketed window - all vanilla ore generation, and any modded ore whose multiplier is 1.0 -
 * reads {@code 1.0} and is left untouched. Uses a mutable single-element array rather than
 * {@code ThreadLocal<Double>} to avoid autoboxing on this per-vein path.
 * <p>
 * This design load-bearingly assumes the vanilla ore feature's {@code place} call - and thus
 * {@code OreFeatureSizeMixin}/{@code ScatteredOreFeatureSizeMixin}'s read of this value -
 * always happens synchronously, on the same thread, within {@code BiomeDecorationOreMixin}'s
 * {@code setSizeMultiplier}/{@code clear} bracket. That holds for every part of Minecraft
 * 1.21.1's chunk-decoration pipeline (feature placement within one biome's decoration step is
 * not parallelized). If a future Minecraft version ever parallelizes decoration across
 * threads - or dispatches a feature's placement asynchronously relative to the call this
 * class is bracketed around - a worker thread would see this class's default (unscaled)
 * {@code 1.0} instead of the intended multiplier, since {@code ThreadLocal} values don't
 * propagate to other threads. Not fixable preemptively without a real parallel chunk-gen
 * pipeline to test against; flagged here so this class is the first place to check if a
 * future Minecraft update ever changes that.
 */
public final class ModdedOreContext {
    private static final ThreadLocal<double[]> SIZE_MULTIPLIER = ThreadLocal.withInitial(() -> new double[] {1.0});

    private ModdedOreContext() {
    }

    public static void setSizeMultiplier(double multiplier) {
        SIZE_MULTIPLIER.get()[0] = multiplier;
    }

    public static double getSizeMultiplier() {
        return SIZE_MULTIPLIER.get()[0];
    }

    public static void clear() {
        SIZE_MULTIPLIER.get()[0] = 1.0;
    }
}
