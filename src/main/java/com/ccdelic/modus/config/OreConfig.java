package com.ccdelic.modus.config;

import com.ccdelic.modus.OreType;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-ore config for Modus. Unlike {@link MobConfig}, each {@link OreType} gets its own
 * independent {@link ModConfigSpec}, registered by {@code Modus} as its own TOML file
 * under {@code config/Modus/oreOptions/<ore>.toml} - so this class builds and holds one
 * spec per ore rather than a single shared one.
 * <p>
 * Each ore's file exposes: enabled, vein size min/max (inclusive), veins attempted per
 * chunk, a 1-in-N rarity chance and the min/max Y level the ore may generate at. All
 * values are read live (via the {@link ModConfigSpec.ConfigValue#get()} accessors
 * returned here) at world generation time, so edits made through the in-game config
 * screen or by hand-editing a TOML file take effect the next time a chunk generates,
 * without needing to touch any datapack file.
 */
public final class OreConfig {
    /**
     * Hard ceiling on vein size, enforced both here (as the declared config range) and
     * again in {@link OreEntry#rollVeinSize}. Vanilla's largest ore vein is 20 blocks
     * (copper_large); empirically, {@link com.ccdelic.modus.worldgen.ConfigurableOreFeature}'s
     * ported vanilla blob algorithm starts writing blocks outside the chunk's safe
     * generation region (logged by Minecraft as "Detected setBlock in a far chunk") well
     * before reaching the old 128 ceiling - confirmed clean at 64 in testing, confirmed
     * broken at 128. 64 is kept as a generous but verified-safe limit.
     * <p>
     * The single shared definition of this limit - {@code com.ccdelic.modus.oregen.ModdedOreRegistry}
     * applies the exact same cap to modded ores' scaled vein sizes rather than redeclaring its
     * own copy, since it's the same underlying vanilla generation boundary either way.
     */
    public static final int MAX_SAFE_VEIN_SIZE = 64;

    private static final Map<OreType, ModConfigSpec> SPECS = new EnumMap<>(OreType.class);
    private static final Map<OreType, OreEntry> ENTRIES = new EnumMap<>(OreType.class);

    static {
        for (OreType ore : OreType.values()) {
            buildEntry(ore);
        }
    }

    private OreConfig() {
    }

    public static ModConfigSpec spec(OreType ore) {
        return SPECS.get(ore);
    }

    public static OreEntry get(OreType ore) {
        return ENTRIES.get(ore);
    }

    private static void buildEntry(OreType ore) {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
            "Settings for " + ore.getSerializedName() + " ore world generation. veinSizeMin/veinSizeMax",
            "control how many blocks a single vein attempt can contain (a random size in that",
            "inclusive range is rolled for every vein). countPerChunk is how many vein attempts",
            "are made per chunk. rarity is an extra 1-in-N chance applied per attempt (rarity=1",
            "means the chance check always passes). minHeight/maxHeight is the inclusive Y",
            "range the ore can generate in."
        );

        ModConfigSpec.BooleanValue enabled = builder
            .comment(
                "Whether Modus's configurable generation replaces vanilla's for " + ore.getSerializedName() + " ore.",
                "false (the default) leaves vanilla's own " + ore.getSerializedName() + " generation completely",
                "untouched - none of the settings below apply until this is set to true."
            )
            .define("enabled", false);

        ModConfigSpec.IntValue veinSizeMin = builder
            .comment("Minimum (inclusive) number of blocks in a single vein.")
            .defineInRange("veinSizeMin", ore.defaultVeinSizeMin(), 0, MAX_SAFE_VEIN_SIZE);

        ModConfigSpec.IntValue veinSizeMax = builder
            .comment("Maximum (inclusive) number of blocks in a single vein.")
            .defineInRange("veinSizeMax", ore.defaultVeinSizeMax(), 0, MAX_SAFE_VEIN_SIZE);

        ModConfigSpec.IntValue countPerChunk = builder
            .comment("Number of vein placement attempts made per chunk.")
            .defineInRange("countPerChunk", ore.defaultCountPerChunk(), 0, 1024);

        ModConfigSpec.IntValue rarity = builder
            .comment("Extra rarity applied per vein attempt, expressed as 1-in-N (1 = always attempt).")
            .defineInRange("rarity", ore.defaultRarity(), 1, 1000);

        ModConfigSpec.IntValue minHeight = builder
            .comment("Minimum (inclusive) Y level this ore can generate at.")
            .defineInRange("minHeight", ore.defaultMinHeight(), -2032, 2032);

        ModConfigSpec.IntValue maxHeight = builder
            .comment("Maximum (inclusive) Y level this ore can generate at.")
            .defineInRange("maxHeight", ore.defaultMaxHeight(), -2032, 2032);

        SPECS.put(ore, builder.build());
        ENTRIES.put(ore, new OreEntry(ore, enabled, veinSizeMin, veinSizeMax, countPerChunk, rarity, minHeight, maxHeight));
    }

    /**
     * Live handle to one ore's config values, plus safeguarded accessors that clamp and
     * self-correct values a user may have set inconsistently by hand (e.g. min > max).
     */
    public record OreEntry(
        OreType ore,
        ModConfigSpec.BooleanValue enabled,
        ModConfigSpec.IntValue veinSizeMin,
        ModConfigSpec.IntValue veinSizeMax,
        ModConfigSpec.IntValue countPerChunk,
        ModConfigSpec.IntValue rarity,
        ModConfigSpec.IntValue minHeight,
        ModConfigSpec.IntValue maxHeight
    ) {
        public boolean isEnabled() {
            return this.enabled.get();
        }

        public int rollVeinSize(net.minecraft.util.RandomSource random) {
            int lo = Math.max(0, Math.min(this.veinSizeMin.get(), this.veinSizeMax.get()));
            int hi = Math.min(MAX_SAFE_VEIN_SIZE, Math.max(this.veinSizeMin.get(), this.veinSizeMax.get()));
            if (hi < lo) {
                hi = lo;
            }
            return lo + random.nextInt(hi - lo + 1);
        }

        public int count() {
            return Math.max(0, this.countPerChunk.get());
        }

        public int rarityChance() {
            return Math.max(1, this.rarity.get());
        }

        /**
         * Returns an inclusive [min, max] height range clamped into the range the current
         * generation context can actually place blocks in, guarding against user-supplied
         * minHeight/maxHeight values that are inverted or outside the world's bounds.
         */
        public int[] clampedHeightRange(int genMin, int genMax) {
            int lo = Math.min(this.minHeight.get(), this.maxHeight.get());
            int hi = Math.max(this.minHeight.get(), this.maxHeight.get());
            lo = net.minecraft.util.Mth.clamp(lo, genMin, genMax);
            hi = net.minecraft.util.Mth.clamp(hi, genMin, genMax);
            if (hi < lo) {
                hi = lo;
            }
            return new int[] {lo, hi};
        }
    }
}
