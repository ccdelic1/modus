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
