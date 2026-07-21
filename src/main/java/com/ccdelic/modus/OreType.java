package com.ccdelic.modus;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Every vanilla ore this mod exposes to config, plus the default generation values
 * used to seed the config file the first time it is created.
 * <p>
 * Vanilla generates most of these ores across several stratified "layers" (e.g. iron has
 * an ore_iron_small, ore_iron_middle and ore_iron_upper layer). This mod deliberately
 * collapses each ore down to a single configurable generation entry so that it can be
 * tuned with one set of knobs; the defaults below are chosen to approximate vanilla's
 * combined frequency/size rather than reproduce every layer exactly.
 * <p>
 * The height range for each ore, though, is meant to be an honest fact, not an
 * approximation - it's exactly the union of every layer that generates for that ore in a
 * <em>typical</em> Overworld/Nether biome (verified directly against vanilla's own
 * {@code worldgen/placed_feature/ore_*.json}), deliberately excluding bonus generation
 * that's gated behind an unusual biome. Gold is the case that prompted this: its "extra"
 * layer (height 32-256) only fires in badlands/eroded badlands/wooded badlands, so a default
 * built from it would show a max height of 256 for gold everywhere, when normal-biome gold
 * (via ore_gold + ore_gold_lower) only ever generates from -64 to 32. Iron and lapis lazuli
 * had a subtler version of the same problem: their default minHeight only accounted for
 * ore_iron_middle (-24) / ore_lapis (-32) and missed ore_iron_small and ore_lapis_buried,
 * both of which - in every normal biome, not just a special one - already reach all the way
 * down to the Overworld's bottom (-64).
 */
public enum OreType implements StringRepresentable {
    COAL("coal", 13, 21, 30, 1, 0, 320),
    IRON("iron", 7, 11, 50, 1, -64, 320),
    COPPER("copper", 8, 20, 16, 1, -16, 112),
    GOLD("gold", 7, 11, 8, 1, -64, 32),
    NETHER_GOLD("nether_gold", 8, 12, 15, 1, 10, 118),
    REDSTONE("redstone", 6, 10, 6, 1, -64, 15),
    DIAMOND("diamond", 4, 12, 6, 1, -64, 16),
    LAPIS("lapis", 5, 9, 3, 1, -64, 64),
    EMERALD("emerald", 2, 4, 100, 1, -16, 320),
    QUARTZ("quartz", 10, 18, 24, 1, 10, 118),
    ANCIENT_DEBRIS("ancient_debris", 2, 3, 3, 1, 8, 120);

    public static final Codec<OreType> CODEC = StringRepresentable.fromEnum(OreType::values);

    private final String id;
    private final int defaultVeinSizeMin;
    private final int defaultVeinSizeMax;
    private final int defaultCountPerChunk;
    private final int defaultRarity;
    private final int defaultMinHeight;
    private final int defaultMaxHeight;

    OreType(
        String id,
        int defaultVeinSizeMin,
        int defaultVeinSizeMax,
        int defaultCountPerChunk,
        int defaultRarity,
        int defaultMinHeight,
        int defaultMaxHeight
    ) {
        this.id = id;
        this.defaultVeinSizeMin = defaultVeinSizeMin;
        this.defaultVeinSizeMax = defaultVeinSizeMax;
        this.defaultCountPerChunk = defaultCountPerChunk;
        this.defaultRarity = defaultRarity;
        this.defaultMinHeight = defaultMinHeight;
        this.defaultMaxHeight = defaultMaxHeight;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    public int defaultVeinSizeMin() {
        return this.defaultVeinSizeMin;
    }

    public int defaultVeinSizeMax() {
        return this.defaultVeinSizeMax;
    }

    public int defaultCountPerChunk() {
        return this.defaultCountPerChunk;
    }

    public int defaultRarity() {
        return this.defaultRarity;
    }

    public int defaultMinHeight() {
        return this.defaultMinHeight;
    }

    public int defaultMaxHeight() {
        return this.defaultMaxHeight;
    }
}
