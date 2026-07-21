package com.ccdelic.modus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ccdelic.modus.structures.StructureFeatureBlacklistContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Marks the window during which a jigsaw structure's {@code feature_pool_element} runs its
 * feature, so {@code WorldGenRegionBlacklistMixin} knows those blocks are structure content
 * (and should get {@link com.ccdelic.modus.structures.StructureBlacklistConfig}'s
 * replacements) rather than ordinary terrain.
 * <p>
 * {@link FeaturePoolElement#place} is a single line - {@code return this.feature.value()
 * .place(level, generator, random, offset)}. Wrapping that one call (rather than injecting at
 * HEAD/RETURN separately) lets the enter/exit sit in a {@code try/finally}, so the context is
 * always balanced even if the feature throws. This targets ALL {@code feature_pool_element}
 * placements, vanilla and modded, so any structure that decorates via a feature is covered -
 * not just vanilla's {@code block_pile} piles.
 * <p>
 * Uses MixinExtras {@code @WrapOperation} rather than a plain {@code @Redirect} so it composes
 * with any other mod that also wraps this call instead of one of them silently failing to
 * apply (see {@code BiomeDecorationOreMixin} for the same reasoning).
 */
@Mixin(FeaturePoolElement.class)
public abstract class FeaturePoolElementBlacklistMixin {
    @WrapOperation(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean modus$bracketFeaturePlacement(
        PlacedFeature feature, WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos,
        Operation<Boolean> original
    ) {
        StructureFeatureBlacklistContext.enter();
        try {
            return original.call(feature, level, generator, random, pos);
        } finally {
            StructureFeatureBlacklistContext.exit();
        }
    }
}
