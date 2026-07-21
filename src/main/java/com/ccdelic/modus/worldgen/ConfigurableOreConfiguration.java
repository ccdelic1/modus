package com.ccdelic.modus.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ccdelic.modus.OreType;
import java.util.List;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

/**
 * Feature configuration for {@link ConfigurableOreFeature} and
 * {@link ConfigurableScatteredOreFeature}. Mirrors vanilla's {@link OreConfiguration}
 * (reusing its target-block-state codec directly) but replaces the fixed {@code size}
 * field with an {@link OreType} key: the actual vein size is rolled live from mod config
 * at placement time instead of being baked into the datapack JSON.
 */
public record ConfigurableOreConfiguration(
    OreType ore,
    List<OreConfiguration.TargetBlockState> targetStates,
    float discardChanceOnAirExposure
) implements FeatureConfiguration {
    public static final Codec<ConfigurableOreConfiguration> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                OreType.CODEC.fieldOf("ore").forGetter(ConfigurableOreConfiguration::ore),
                OreConfiguration.TargetBlockState.CODEC.listOf().fieldOf("targets").forGetter(ConfigurableOreConfiguration::targetStates),
                Codec.floatRange(0.0F, 1.0F)
                    .fieldOf("discard_chance_on_air_exposure")
                    .forGetter(ConfigurableOreConfiguration::discardChanceOnAirExposure)
            )
            .apply(instance, ConfigurableOreConfiguration::new)
    );
}
