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
 * <p>
 * One deliberate deviation from vanilla's {@code ScatteredOreFeature#place}: that method
 * always returns {@code true}, even when every attempt failed {@code canPlaceOre} and zero
 * blocks were placed (unlike vanilla's own {@link OreFeature#doPlace}, which accurately
 * returns whether it placed anything - confirmed by reading both in the decompiled vanilla
 * source). This tracks the same kind of placed-count {@link ConfigurableOreFeature#doPlace}
 * already does and returns whether anything actually got placed, so a caller (or another mod
 * wrapping this feature's placement) can tell a genuine placement apart from a vein that
 * rolled a valid size but found nowhere it could actually go.
 * <p>
 * The {@code setBlock(mutablePos, target.state, 2)} update flag below is deliberately NOT
 * changed to match {@link ConfigurableOreFeature#doPlace}'s {@code setBlockState(..., false)}:
 * confirmed by reading both in the decompiled vanilla source, vanilla's own
 * {@code ScatteredOreFeature#place} and {@link OreFeature#doPlace} already use these exact
 * same two different flags for their respective feature types (flag {@code 2}, sending a
 * block update, here; flag {@code false}, sending none, there) - this class faithfully mirrors
 * that, rather than introducing its own inconsistency. Unlike the return-value fix above,
 * unifying these flags would be a genuine world-generation behavior change (whether neighbor
 * block updates, redstone/fluid/gravity reactions, and other mods' block-change listeners fire
 * during chunk generation), with no vanilla precedent for which of the two behaviors would be
 * "more correct" to converge on - so, deliberately, this stays exactly as faithful to vanilla
 * as the rest of this port.
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
        int placed = 0;

        for (int i = 0; i < attempts; i++) {
            offsetTargetPos(mutablePos, random, origin, Math.min(i, MAX_DIST_FROM_ORIGIN));
            BlockState currentState = level.getBlockState(mutablePos);

            for (OreConfiguration.TargetBlockState target : vanillaConfig.targetStates) {
                if (OreFeature.canPlaceOre(currentState, level::getBlockState, random, vanillaConfig, target, mutablePos)) {
                    level.setBlock(mutablePos, target.state, 2);
                    placed++;
                    break;
                }
            }
        }

        return placed > 0;
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
