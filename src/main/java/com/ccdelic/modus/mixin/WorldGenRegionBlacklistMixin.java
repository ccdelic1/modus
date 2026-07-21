package com.ccdelic.modus.mixin;

import com.ccdelic.modus.structures.StructureBlacklistConfig;
import com.ccdelic.modus.structures.StructureFeatureBlacklistContext;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Applies {@link StructureBlacklistConfig}'s replacements to blocks a structure decoration
 * feature places directly through {@code setBlock} - the third and last block-placement path
 * (after templates and hand-coded {@code StructurePiece} pieces), used by things like a
 * village's {@code block_pile} hay/pumpkin/snow piles.
 * <p>
 * {@code WorldGenRegion.setBlock} is THE choke point for all block placement during world
 * generation, so this only rewrites the block state while
 * {@link StructureFeatureBlacklistContext} is active - i.e. only during a jigsaw structure's
 * feature placement (bracketed by {@code FeaturePoolElementBlacklistMixin}). Every other
 * {@code setBlock} - terrain, carvers, ordinary biome features (ore, trees) placed outside a
 * structure - passes through untouched, keeping the blacklist a structures-only tool.
 * <p>
 * Because this sits on a genuinely hot path, it checks
 * {@link StructureBlacklistConfig#hasReplacements()} FIRST: when replacement is off or the
 * list is empty (the default), that's a couple of cheap reads and we return immediately,
 * before even the {@link ThreadLocal} lookup {@link StructureFeatureBlacklistContext#isActive}
 * does - so users who don't use this feature pay essentially nothing.
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionBlacklistMixin {
    @ModifyVariable(method = "setBlock", at = @At("HEAD"), argsOnly = true)
    private BlockState modus$applyStructureFeatureBlacklist(BlockState state) {
        if (!StructureBlacklistConfig.hasReplacements() || !StructureFeatureBlacklistContext.isActive()) {
            return state;
        }
        Block replacement = StructureBlacklistConfig.replacementFor(state.getBlock());
        return replacement != null ? replacement.defaultBlockState() : state;
    }
}
