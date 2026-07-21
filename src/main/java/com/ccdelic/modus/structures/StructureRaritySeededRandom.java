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

    /** FNV-1a 64-bit offset basis - see {@link #stableHash}. */
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    /** FNV-1a 64-bit prime - see {@link #stableHash}. */
    private static final long FNV_PRIME = 0x100000001b3L;

    private StructureRaritySeededRandom() {
    }

    public static RandomSource create(long levelSeed, ChunkPos chunkPos, ResourceLocation structureId, long salt) {
        long comboSeed = levelSeed;
        comboSeed = comboSeed * 31L + chunkPos.x;
        comboSeed = comboSeed * 31L + chunkPos.z;
        comboSeed = comboSeed * 31L + stableHash(structureId.toString());
        comboSeed = comboSeed * 31L + salt;
        return RandomSource.create(comboSeed);
    }

    /**
     * A hash Modus defines and owns outright, rather than {@code String#hashCode()} -
     * whose specific algorithm is a documented part of the {@code java.lang.String} API
     * contract that no compliant JDK has ever changed, but which this deliberately doesn't
     * lean on anyway: this seed feeds directly into per-world, persistent structure-rarity
     * rolls, the worst possible place to accept even a purely theoretical risk of a value
     * silently changing out from under an existing world. Using FNV-1a here instead means
     * the algorithm can only ever change if Modus itself changes it.
     */
    private static long stableHash(String value) {
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
