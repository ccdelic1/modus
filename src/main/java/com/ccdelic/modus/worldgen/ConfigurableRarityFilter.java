package com.ccdelic.modus.worldgen;

import com.mojang.serialization.MapCodec;
import com.ccdelic.modus.OreType;
import com.ccdelic.modus.config.OreConfig;
import com.ccdelic.modus.registry.ModPlacementModifierTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * A 1-in-N rarity check (mirrors vanilla {@code RarityFilter}) whose {@code N} is read
 * live from mod config for the given {@link OreType}. Also blocks placement outright
 * when the ore is disabled, so this filter alone is enough to fully disable an ore even
 * if {@link ConfigurableCountPlacement} were bypassed some other way.
 */
public class ConfigurableRarityFilter extends PlacementFilter {
    public static final MapCodec<ConfigurableRarityFilter> CODEC =
        OreType.CODEC.fieldOf("ore").xmap(ConfigurableRarityFilter::new, ConfigurableRarityFilter::ore);

    private final OreType ore;

    public ConfigurableRarityFilter(OreType ore) {
        this.ore = ore;
    }

    public OreType ore() {
        return this.ore;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        OreConfig.OreEntry entry = OreConfig.get(this.ore);
        if (!entry.isEnabled()) {
            return false;
        }
        int chance = entry.rarityChance();
        return chance <= 1 || random.nextFloat() < 1.0F / (float) chance;
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacementModifierTypes.CONFIGURABLE_RARITY_FILTER.get();
    }
}
