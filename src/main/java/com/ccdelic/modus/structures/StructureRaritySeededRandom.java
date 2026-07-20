package com.ccdelic.modus.structures;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;

/**
 * Deterministic per-(seed, chunk, structure, purpose) random source shared by every place
 * {@link StructureRarityRegistry}'s rarity is rolled against. Using one shared source is
 * what lets {@code StructureCheckMixin}'s cheap pre-check (run during searches like
 * {@code /locate}, before real chunk generation) and {@code ChunkGeneratorMixin}'s actual
 * veto (run during real chunk generation) agree on the same outcome for the same chunk
 * without either one needing to know about the other - same inputs always produce the same
 * roll, so a candidate the pre-check predicts will be vetoed is always the one the real veto
 * goes on to veto, and vice versa.
 */
public final class StructureRaritySeededRandom {
    public static final long VETO_SALT = 0x4D6F6455L;
    public static final long EXTRA_ATTEMPT_SALT = 0x45787472614AL;

    private StructureRaritySeededRandom() {
    }

    public static RandomSource create(long levelSeed, ChunkPos chunkPos, ResourceLocation structureId, long salt) {
        long comboSeed = levelSeed;
        comboSeed = comboSeed * 31L + chunkPos.x;
        comboSeed = comboSeed * 31L + chunkPos.z;
        comboSeed = comboSeed * 31L + structureId.hashCode();
        comboSeed = comboSeed * 31L + salt;
        return RandomSource.create(comboSeed);
    }
}
