package com.ccdelic.modus.structures;

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

    public static void exit() {
        int[] cell = DEPTH.get();
        if (cell[0] > 0) {
            cell[0]--;
        }
    }

    public static boolean isActive() {
        return DEPTH.get()[0] > 0;
    }
}
