package com.ccdelic.modus.worldgen;

import com.mojang.serialization.Codec;
import com.ccdelic.modus.config.OreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

/**
 * A near line-for-line port of vanilla {@link ScatteredOreFeature} (used for ancient
 * debris), reading its {@code size} live from mod config instead of a fixed datapack
 * value, mirroring {@link ConfigurableOreFeature}.
 */
public class ConfigurableScatteredOreFeature extends Feature<ConfigurableOreConfiguration> {
    private static final int MAX_DIST_FROM_ORIGIN = 7;

    public ConfigurableScatteredOreFeature(Codec<ConfigurableOreConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ConfigurableOreConfiguration> context) {
        ConfigurableOreConfiguration config = context.config();
        OreConfig.OreEntry entry = OreConfig.get(config.ore());
        if (!entry.isEnabled()) {
            return false;
        }

        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        int size = Math.max(0, entry.rollVeinSize(random));
        if (size <= 0) {
            return false;
        }
        OreConfiguration vanillaConfig = new OreConfiguration(config.targetStates(), size, config.discardChanceOnAirExposure());

        int attempts = random.nextInt(size + 1);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < attempts; i++) {
            offsetTargetPos(mutablePos, random, origin, Math.min(i, MAX_DIST_FROM_ORIGIN));
            BlockState currentState = level.getBlockState(mutablePos);

            for (OreConfiguration.TargetBlockState target : vanillaConfig.targetStates) {
                if (OreFeature.canPlaceOre(currentState, level::getBlockState, random, vanillaConfig, target, mutablePos)) {
                    level.setBlock(mutablePos, target.state, 2);
                    break;
                }
            }
        }

        return true;
    }

    private static void offsetTargetPos(BlockPos.MutableBlockPos mutablePos, RandomSource random, BlockPos origin, int magnitude) {
        int x = randomOffset(random, magnitude);
        int y = randomOffset(random, magnitude);
        int z = randomOffset(random, magnitude);
        mutablePos.setWithOffset(origin, x, y, z);
    }

    private static int randomOffset(RandomSource random, int magnitude) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) magnitude);
    }
}
