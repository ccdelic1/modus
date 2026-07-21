package com.ccdelic.modus.worldgen;

import com.mojang.serialization.MapCodec;
import com.ccdelic.modus.OreType;
import com.ccdelic.modus.config.OreConfig;
import com.ccdelic.modus.registry.ModPlacementModifierTypes;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Picks a uniformly random Y in the configured [minHeight, maxHeight] inclusive range for
 * the given {@link OreType}, read live from mod config. Unlike vanilla's
 * {@code HeightRangePlacement} (which resolves {@code VerticalAnchor}s baked into JSON),
 * this clamps the configured range into whatever the active generation context can
 * actually place blocks in, so an out-of-range or inverted config value can never throw -
 * it degenerates gracefully to the nearest valid Y.
 */
public class ConfigurableHeightRangePlacement extends PlacementModifier {
    public static final MapCodec<ConfigurableHeightRangePlacement> CODEC =
        OreType.CODEC.fieldOf("ore").xmap(ConfigurableHeightRangePlacement::new, ConfigurableHeightRangePlacement::ore);

    private final OreType ore;

    public ConfigurableHeightRangePlacement(OreType ore) {
        this.ore = ore;
    }

    public OreType ore() {
        return this.ore;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        OreConfig.OreEntry entry = OreConfig.get(this.ore);
        if (!entry.isEnabled()) {
            return Stream.empty();
        }

        int genMin = context.getMinGenY();
        int genMax = genMin + context.getGenDepth() - 1;
        int[] range = entry.clampedHeightRange(genMin, genMax);
        int y = range[0] + random.nextInt(range[1] - range[0] + 1);
        return Stream.of(pos.atY(y));
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModPlacementModifierTypes.CONFIGURABLE_HEIGHT_RANGE.get();
    }
}
