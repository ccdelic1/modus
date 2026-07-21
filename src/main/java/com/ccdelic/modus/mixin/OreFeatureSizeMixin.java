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
 * <p>
 * {@code ordinal = 0} is explicit here rather than left as Mixin's implicit default: as of
 * 1.21.1, {@code OreFeature.place} declares exactly one {@code OreConfiguration}-typed local
 * (confirmed by reading the decompiled method), so today this selects the only candidate
 * either way. This is NOT a safety improvement over leaving the ordinal unset, though - if
 * anything it trades one direction of safety for the opposite one. Left unset, Mixin
 * requires exactly one candidate of the matching type and hard-fails at mixin-application
 * time if a future Minecraft update ever introduces a second one - exactly the loud,
 * immediate failure {@code modus.mixins.json}'s {@code required: true} / {@code
 * defaultRequire: 1} is meant to guarantee. With an explicit ordinal, that ambiguity check no
 * longer applies: Mixin simply selects the (ordinal)-th matching candidate and proceeds, so a
 * future second candidate introduced BEFORE this one would be silently targeted instead of
 * the intended one - the opposite of failing loud. Kept anyway purely for self-documentation
 * (the value actually used is spelled out, not left implicit), not because it's safer
 * against a future ambiguity - it verifiably is not.
 */
@Mixin(OreFeature.class)
public abstract class OreFeatureSizeMixin {
    @ModifyVariable(method = "place", at = @At("STORE"), ordinal = 0)
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
