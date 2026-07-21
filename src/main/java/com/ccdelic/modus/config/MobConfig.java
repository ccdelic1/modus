package com.ccdelic.modus.config;

import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Single config file (config/Modus/mobOptions/mobs.toml) holding every tweakable mob
 * spawn cap value. Unlike {@link OreConfig} - which is one spec per ore - all mob spawn
 * cap values live together in one file, per the requested layout.
 * <p>
 * Defaults mirror vanilla's own {@link MobCategory#getMaxInstancesPerChunk()} values, so
 * installing the mod changes nothing until a value is edited. {@link MobCategory#MISC} is
 * intentionally not exposed: vanilla uses -1 there as a sentinel for "no per-chunk cap"
 * (bats, cats, etc.), and overriding it would not behave like a normal cap.
 */
public final class MobConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Master switch for mob spawn cap overrides. false (the default) leaves every category at its vanilla maximum.")
        .define("enabled", false);

    public static final ModConfigSpec.IntValue MONSTER_MAX = BUILDER
        .comment("Maximum MONSTER category mobs (zombies, skeletons, creepers, ...) per chunk. Vanilla default: 70.")
        .defineInRange("monsterMax", MobCategory.MONSTER.getMaxInstancesPerChunk(), 1, 350);

    public static final ModConfigSpec.IntValue CREATURE_MAX = BUILDER
        .comment("Maximum CREATURE category mobs (cows, pigs, sheep, ...) per chunk. Vanilla default: 10.")
        .defineInRange("creatureMax", MobCategory.CREATURE.getMaxInstancesPerChunk(), 1, 350);

    public static final ModConfigSpec.IntValue AMBIENT_MAX = BUILDER
        .comment("Maximum AMBIENT category mobs (bats) per chunk. Vanilla default: 15.")
        .defineInRange("ambientMax", MobCategory.AMBIENT.getMaxInstancesPerChunk(), 1, 350);

    public static final ModConfigSpec.IntValue AXOLOTLS_MAX = BUILDER
        .comment("Maximum AXOLOTLS category mobs per chunk. Vanilla default: 5.")
        .defineInRange("axolotlsMax", MobCategory.AXOLOTLS.getMaxInstancesPerChunk(), 1, 350);

    public static final ModConfigSpec.IntValue UNDERGROUND_WATER_CREATURE_MAX = BUILDER
        .comment("Maximum UNDERGROUND_WATER_CREATURE category mobs (glow squid) per chunk. Vanilla default: 5.")
        .defineInRange("undergroundWaterCreatureMax", MobCategory.UNDERGROUND_WATER_CREATURE.getMaxInstancesPerChunk(), 1, 350);

    public static final ModConfigSpec.IntValue WATER_CREATURE_MAX = BUILDER
        .comment("Maximum WATER_CREATURE category mobs (squid, dolphins) per chunk. Vanilla default: 5.")
        .defineInRange("waterCreatureMax", MobCategory.WATER_CREATURE.getMaxInstancesPerChunk(), 1, 350);

    public static final ModConfigSpec.IntValue WATER_AMBIENT_MAX = BUILDER
        .comment("Maximum WATER_AMBIENT category mobs (fish schools) per chunk. Vanilla default: 20.")
        .defineInRange("waterAmbientMax", MobCategory.WATER_AMBIENT.getMaxInstancesPerChunk(), 1, 350);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * Sentinel returned by {@link #maxFor} for a category this mod has no config value
     * for. {@link MobCategory} implements NeoForge's {@code IExtensibleEnum}, so other
     * installed mods can register brand new categories at runtime; distinct from any
     * value {@link #maxFor} could otherwise return (including out-of-range hand-edited
     * ones), so callers can tell "not ours to manage" apart from "misconfigured".
     */
    public static final int UNSUPPORTED_CATEGORY = Integer.MIN_VALUE;

    private MobConfig() {
    }

    /**
     * Returns the configured cap for the given category, or {@link #UNSUPPORTED_CATEGORY}
     * if this mod does not expose a config value for it - either {@link MobCategory#MISC}
     * or a category added by another mod.
     */
    public static int maxFor(MobCategory category) {
        return switch (category) {
            case MONSTER -> MONSTER_MAX.get();
            case CREATURE -> CREATURE_MAX.get();
            case AMBIENT -> AMBIENT_MAX.get();
            case AXOLOTLS -> AXOLOTLS_MAX.get();
            case UNDERGROUND_WATER_CREATURE -> UNDERGROUND_WATER_CREATURE_MAX.get();
            case WATER_CREATURE -> WATER_CREATURE_MAX.get();
            case WATER_AMBIENT -> WATER_AMBIENT_MAX.get();
            default -> UNSUPPORTED_CATEGORY;
        };
    }
}
