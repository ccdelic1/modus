package com.ccdelic.modus.mixin;

import com.ccdelic.modus.config.OreConfig;
import com.ccdelic.modus.oregen.ModdedOreContext;
import com.ccdelic.modus.oregen.ModdedOreRegistry;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Applies {@link ModdedOreRegistry}'s {@code sizeMultiplier} to a vanilla-ore-feature vein as
 * it generates. {@code OreFeature.place} reads its {@link OreConfiguration}'s {@code size}
 * from a single local (assigned once from {@code context.config()}, then used throughout and
 * passed to the placement loop), so swapping that local for a size-scaled copy scales the
 * whole vein consistently with one hook.
 * <p>
 * Only acts while {@link ModdedOreContext} carries a non-1.0 multiplier - i.e. only during a
 * managed modded ore's placement, bracketed by {@code BiomeDecorationOreMixin}. Every other
 * {@code OreFeature.place} (all vanilla ore generation, plus any modded ore left at the
 * default multiplier) sees {@code 1.0} and is returned untouched. The effective size is
 * clamped to {@link OreConfig#MAX_SAFE_VEIN_SIZE} to avoid the far-chunk crash big veins can
 * trigger.
 */
@Mixin(OreFeature.class)
public abstract class OreFeatureSizeMixin {
    @ModifyVariable(method = "place", at = @At("STORE"))
    private OreConfiguration modus$scaleVeinSize(OreConfiguration config) {
        double multiplier = ModdedOreContext.getSizeMultiplier();
        if (multiplier == 1.0) {
            return config;
        }
        int scaled = Mth.clamp((int) Math.round(config.size * multiplier), 0, OreConfig.MAX_SAFE_VEIN_SIZE);
        if (scaled == config.size) {
            return config;
        }
        return new OreConfiguration(config.targetStates, scaled, config.discardChanceOnAirExposure);
    }
}
