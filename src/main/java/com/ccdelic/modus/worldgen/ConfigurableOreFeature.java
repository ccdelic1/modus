package com.ccdelic.modus.worldgen;

import com.mojang.serialization.Codec;
import com.ccdelic.modus.config.OreConfig;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

/**
 * A near line-for-line port of vanilla {@link OreFeature}'s vein-placement algorithm.
 * The only behavioral change is that the vein's {@code size} is rolled live from mod
 * config (a random value in the configured [min, max] inclusive range) instead of being
 * a fixed number baked into the configured feature's JSON, and the target block states /
 * air-exposure discard chance come from {@link ConfigurableOreConfiguration} instead of
 * vanilla's {@link OreConfiguration}. The actual air-exposure/placement checks are
 * delegated to vanilla's {@link OreFeature#canPlaceOre} to stay behaviorally identical.
 */
public class ConfigurableOreFeature extends Feature<ConfigurableOreConfiguration> {
    public ConfigurableOreFeature(Codec<ConfigurableOreConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ConfigurableOreConfiguration> context) {
        ConfigurableOreConfiguration config = context.config();
        OreConfig.OreEntry entry = OreConfig.get(config.ore());
        if (!entry.isEnabled()) {
            return false;
        }

        RandomSource random = context.random();
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();

        int size = Math.max(0, entry.rollVeinSize(random));
        if (size <= 0) {
            return false;
        }
        OreConfiguration vanillaConfig = new OreConfiguration(config.targetStates(), size, config.discardChanceOnAirExposure());

        float f = random.nextFloat() * (float) Math.PI;
        float f1 = (float) size / 8.0F;
        int i = Mth.ceil(((float) size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d0 = origin.getX() + Math.sin(f) * f1;
        double d1 = origin.getX() - Math.sin(f) * f1;
        double d2 = origin.getZ() + Math.cos(f) * f1;
        double d3 = origin.getZ() - Math.cos(f) * f1;
        double d4 = origin.getY() + random.nextInt(3) - 2;
        double d5 = origin.getY() + random.nextInt(3) - 2;
        int k = origin.getX() - Mth.ceil(f1) - i;
        int l = origin.getY() - 2 - i;
        int i1 = origin.getZ() - Mth.ceil(f1) - i;
        int j1 = 2 * (Mth.ceil(f1) + i);
        int k1 = 2 * (2 + i);

        for (int l1 = k; l1 <= k + j1; l1++) {
            for (int i2 = i1; i2 <= i1 + j1; i2++) {
                if (l <= level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, l1, i2)) {
                    return doPlace(level, random, vanillaConfig, d0, d1, d2, d3, d4, d5, k, l, i1, j1, k1);
                }
            }
        }

        return false;
    }

    private static boolean doPlace(
        WorldGenLevel level,
        RandomSource random,
        OreConfiguration config,
        double minX,
        double maxX,
        double minZ,
        double maxZ,
        double minY,
        double maxY,
        int x,
        int y,
        int z,
        int width,
        int height
    ) {
        int placed = 0;
        BitSet bitset = new BitSet(width * height * width);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int size = config.size;
        double[] points = new double[size * 4];

        for (int k = 0; k < size; k++) {
            float f = (float) k / (float) size;
            double d0 = Mth.lerp(f, minX, maxX);
            double d1 = Mth.lerp(f, minY, maxY);
            double d2 = Mth.lerp(f, minZ, maxZ);
            double d3 = random.nextDouble() * (double) size / 16.0;
            double d4 = ((Mth.sin((float) Math.PI * f) + 1.0F) * d3 + 1.0) / 2.0;
            points[k * 4] = d0;
            points[k * 4 + 1] = d1;
            points[k * 4 + 2] = d2;
            points[k * 4 + 3] = d4;
        }

        for (int a = 0; a < size - 1; a++) {
            if (!(points[a * 4 + 3] <= 0.0)) {
                for (int b = a + 1; b < size; b++) {
                    if (!(points[b * 4 + 3] <= 0.0)) {
                        double d8 = points[a * 4] - points[b * 4];
                        double d10 = points[a * 4 + 1] - points[b * 4 + 1];
                        double d12 = points[a * 4 + 2] - points[b * 4 + 2];
                        double d14 = points[a * 4 + 3] - points[b * 4 + 3];
                        if (d14 * d14 > d8 * d8 + d10 * d10 + d12 * d12) {
                            if (d14 > 0.0) {
                                points[b * 4 + 3] = -1.0;
                            } else {
                                points[a * 4 + 3] = -1.0;
                            }
                        }
                    }
                }
            }
        }

        try (BulkSectionAccess bulkAccess = new BulkSectionAccess(level)) {
            for (int c = 0; c < size; c++) {
                double radius = points[c * 4 + 3];
                if (!(radius < 0.0)) {
                    double cx = points[c * 4];
                    double cy = points[c * 4 + 1];
                    double cz = points[c * 4 + 2];
                    int minXi = Math.max(Mth.floor(cx - radius), x);
                    int minYi = Math.max(Mth.floor(cy - radius), y);
                    int minZi = Math.max(Mth.floor(cz - radius), z);
                    int maxXi = Math.max(Mth.floor(cx + radius), minXi);
                    int maxYi = Math.max(Mth.floor(cy + radius), minYi);
                    int maxZi = Math.max(Mth.floor(cz + radius), minZi);

                    for (int i2 = minXi; i2 <= maxXi; i2++) {
                        double dx = (i2 + 0.5 - cx) / radius;
                        if (dx * dx < 1.0) {
                            for (int j2 = minYi; j2 <= maxYi; j2++) {
                                double dy = (j2 + 0.5 - cy) / radius;
                                if (dx * dx + dy * dy < 1.0) {
                                    for (int k2 = minZi; k2 <= maxZi; k2++) {
                                        double dz = (k2 + 0.5 - cz) / radius;
                                        if (dx * dx + dy * dy + dz * dz < 1.0 && !level.isOutsideBuildHeight(j2)) {
                                            int index = i2 - x + (j2 - y) * width + (k2 - z) * width * height;
                                            if (!bitset.get(index)) {
                                                bitset.set(index);
                                                mutablePos.set(i2, j2, k2);
                                                if (level.ensureCanWrite(mutablePos)) {
                                                    LevelChunkSection section = bulkAccess.getSection(mutablePos);
                                                    if (section != null) {
                                                        int rx = SectionPos.sectionRelative(i2);
                                                        int ry = SectionPos.sectionRelative(j2);
                                                        int rz = SectionPos.sectionRelative(k2);
                                                        BlockState currentState = section.getBlockState(rx, ry, rz);

                                                        for (OreConfiguration.TargetBlockState target : config.targetStates) {
                                                            if (OreFeature.canPlaceOre(
                                                                currentState, bulkAccess::getBlockState, random, config, target, mutablePos
                                                            )) {
                                                                section.setBlockState(rx, ry, rz, target.state, false);
                                                                placed++;
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return placed > 0;
    }
}
