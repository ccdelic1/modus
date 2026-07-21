package com.ccdelic.modus.mixin;

import com.ccdelic.modus.config.OreConfig;
import com.ccdelic.modus.oregen.ModdedOreContext;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The {@link ScatteredOreFeature} counterpart of {@code OreFeatureSizeMixin} - some ores
 * (vanilla nether gold/quartz, and any modded ore using the scattered variant) generate via
 * this feature instead. Same single-local {@link OreConfiguration} pattern, same
 * {@link ModdedOreContext}-gated scaling and {@link OreConfig#MAX_SAFE_VEIN_SIZE} clamp - see
 * that class for the rationale, including why {@code ordinal = 0} is spelled out explicitly.
 */
@Mixin(ScatteredOreFeature.class)
public abstract class ScatteredOreFeatureSizeMixin {
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
