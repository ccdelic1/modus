package com.ccdelic.modus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ccdelic.modus.oregen.ModdedOreContext;
import com.ccdelic.modus.oregen.ModdedOreRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies {@link ModdedOreRegistry}'s per-ore enable/disable and frequency multiplier to
 * modded ores, by wrapping each placed-feature placement in
 * {@code ChunkGenerator.applyBiomeDecoration} - the one spot where a biome's features are
 * actually run per chunk, and where the {@code PlacedFeature} being placed is in hand.
 * <p>
 * Wrapping the single {@code placeWithBiomeCheck} call (the structure-placement branch of the
 * same method uses a different call) lets this decide, per feature:
 * <ul>
 *   <li>not a managed modded ore - place normally (the overwhelmingly common case: every
 *   non-ore feature, and every ore feature in a non-modpack world, pays for exactly one
 *   {@link ModdedOreRegistry#get} lookup and nothing else - see that method for why an extra
 *   "is anything tracked at all" pre-check isn't worth doing here).</li>
 *   <li>disabled - skip entirely.</li>
 *   <li>frequency &gt; 1 - run the placement that many times (each extra run continues from
 *   the now-advanced random, so it lands different veins rather than re-placing the same
 *   ones); a fractional part becomes a probabilistic extra run.</li>
 *   <li>frequency &lt; 1 - run it only with that probability.</li>
 * </ul>
 * At {@code frequency == 1.0} and {@code sizeMultiplier == 1.0} (the default) it delegates
 * straight to one normal call, byte-for-byte identical to vanilla, so an untouched modded ore
 * generates exactly as the mod intended. The size multiplier is handed to
 * {@link ModdedOreContext} for {@code OreFeatureSizeMixin} to pick up during the actual vein
 * roll.
 * <p>
 * Uses MixinExtras {@code @WrapOperation} rather than a plain {@code @Redirect} so it composes
 * with other mods that hook the same call: {@code @Redirect} is exclusive (a second mod
 * redirecting {@code placeWithBiomeCheck} here would fail to apply), while wrappers chain.
 * That matters for a world-gen mod likely to share a pack with other world-gen mods.
 */
@Mixin(ChunkGenerator.class)
public abstract class BiomeDecorationOreMixin {
    @WrapOperation(
        method = "applyBiomeDecoration",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;placeWithBiomeCheck(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean modus$applyModdedOreConfig(
        PlacedFeature feature, WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos,
        Operation<Boolean> original
    ) {
        ModdedOreRegistry.Entry entry = ModdedOreRegistry.get(feature);
        if (entry == null) {
            return original.call(feature, level, generator, random, pos);
        }
        if (!entry.enabled()) {
            return false;
        }

        double frequency = entry.frequencyMultiplier();
        double sizeMultiplier = entry.sizeMultiplier();
        if (frequency == 1.0 && sizeMultiplier == 1.0) {
            return original.call(feature, level, generator, random, pos);
        }

        ModdedOreContext.setSizeMultiplier(sizeMultiplier);
        try {
            int fullRuns = (int) Math.floor(frequency);
            double fractionalRun = frequency - fullRuns;
            boolean placedAny = false;
            for (int run = 0; run < fullRuns; run++) {
                placedAny |= original.call(feature, level, generator, random, pos);
            }
            if (fractionalRun > 0.0 && random.nextDouble() < fractionalRun) {
                placedAny |= original.call(feature, level, generator, random, pos);
            }
            return placedAny;
        } finally {
            ModdedOreContext.clear();
        }
    }
}
