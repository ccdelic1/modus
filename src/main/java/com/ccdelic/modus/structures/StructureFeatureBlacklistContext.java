package com.ccdelic.modus.structures;

import com.ccdelic.modus.Modus;

/**
 * A thread-scoped "we are currently placing a jigsaw structure's decoration feature" flag,
 * so {@code WorldGenRegionBlacklistMixin} can apply {@link StructureBlacklistConfig}'s block
 * replacements to blocks a feature sets DIRECTLY (via {@code level.setBlock}) rather than
 * through a structure template.
 * <p>
 * Villages place their big decorative piles (hay, pumpkin, melon, snow, ice) not as blocks
 * baked into a template - which {@code StructureBlacklistMixin} already handles - but as a
 * {@code feature_pool_element} that runs a {@code block_pile} feature, whose blocks go
 * straight to {@code WorldGenRegion.setBlock}, bypassing both the template path and the
 * hand-coded-{@code StructurePiece.placeBlock} path. Confirmed empirically: a village's
 * template hay is replaced, its feature-pile hay was not, until this.
 * <p>
 * {@code FeaturePoolElementBlacklistMixin} brackets each such feature placement with
 * {@link #enter()}/{@link #exit()}; the {@code setBlock} mixin only rewrites blocks while the
 * flag is set, so ordinary world generation (ore veins, trees, and every other feature placed
 * outside a jigsaw structure) is never touched - the blacklist stays a structures-only tool.
 * A depth counter rather than a plain boolean guards against the theoretical case of a
 * structure feature nesting another feature placement, so the outer scope isn't cleared early.
 */
public final class StructureFeatureBlacklistContext {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private StructureFeatureBlacklistContext() {
    }

    public static void enter() {
        DEPTH.get()[0]++;
    }

    /**
     * A misbehaving third-party mod calling this without a matching {@link #enter()} first
     * (e.g. by poking at internal state some other way) is deliberately never let drive the
     * depth counter negative - doing so would need to be balanced out by an equal number of
     * extra {@link #enter()} calls before the blacklist could ever apply again, effectively
     * disabling it. But an unbalanced call from Modus's OWN mixin code (a bug: an exception
     * path between {@code enter()}/{@code exit()}, or a duplicate {@code exit()}) would
     * previously pass just as silently as a third party's - logging here means one is loud in
     * the log and the other never occurs at all, instead of both looking identical.
     * <p>
     * The logging call itself is wrapped so it can never cause THIS method to throw: {@code
     * FeaturePoolElementBlacklistMixin} calls this from a {@code finally} block specifically
     * so the depth counter always stays balanced even if the wrapped feature placement threw
     * - if {@code exit()} itself could throw (however unlikely, e.g. a broken log appender),
     * that guarantee would be undermined for the one call site that exists to provide it.
     */
    public static void exit() {
        int[] cell = DEPTH.get();
        if (cell[0] > 0) {
            cell[0]--;
        } else {
            try {
                Modus.LOGGER.warn(
                    "Modus: StructureFeatureBlacklistContext#exit() called without a matching enter() on thread \"{}\"; ignoring.",
                    Thread.currentThread().getName()
                );
            } catch (RuntimeException e) {
                // Deliberately swallowed - see the javadoc above. A failure to log a warning
                // must never itself become a bigger problem than the warning was.
            }
        }
    }

    public static boolean isActive() {
        return DEPTH.get()[0] > 0;
    }
}
