package com.ccdelic.modus.worldgen;

import com.mojang.serialization.MapCodec;
import com.ccdelic.modus.OreType;
import com.ccdelic.modus.config.OreConfig;
import com.ccdelic.modus.registry.ModPlacementModifierTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

/**
 * Placement step that repeats {@code countPerChunk} times, reading the count live from
 * mod config for the given {@link OreType} instead of a fixed value baked into the
 * placed feature's JSON. Returns 0 (no attempts) when the ore is disabled.
 */
public class ConfigurableCountPlacement extends RepeatingPlacement {
    public static final MapCodec<ConfigurableCountPlacement> CODEC =
        OreType.CODEC.fieldOf("ore").xmap(ConfigurableCountPlacement::new, ConfigurableCountPlacement::ore);

    private final OreType ore;

    public ConfigurableCountPlacement(OreType ore) {
        this.ore = ore;
    }

    public OreType ore() {
        return this.ore;
    }

    @Override
    protected int count(RandomSource random, BlockPos pos) {
        OreConfig.OreEntry entry = OreConfig.get(this.ore);
        if (!entry.isEnabled()) {
            return 0;
        }
        return entry.count();
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacementModifierTypes.CONFIGURABLE_COUNT.get();
    }
}
