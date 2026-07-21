package com.ccdelic.modus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Single config file (config/Modus/biomeSize/biomeSize.toml) controlling a global biome size
 * modifier. See {@link com.ccdelic.modus.mixin.BiomeSizeMixin} for how this is applied - it
 * shifts the {@code firstOctave} of the {@code minecraft:temperature} and
 * {@code minecraft:vegetation} noise fields that vanilla's climate sampler uses to pick which
 * biome generates where, which - since every dimension (vanilla's Overworld, and every biome
 * a mod adds to it, via TerraBlender or vanilla's own multi-noise biome source) samples those
 * exact same shared noise registry entries - makes every biome, vanilla or modded, bigger or
 * smaller together, with no per-biome or per-mod configuration needed.
 * <p>
 * A positive modifier makes the underlying noise coarser (each step lowers the effective
 * octave, roughly doubling the spatial scale per step) so biomes get bigger; negative makes
 * them smaller. 0 is vanilla, exactly - matching every other feature's own convention of
 * defaulting to a true no-op until a player opts in.
 */
public final class BiomeSizeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Master switch for biome size scaling. false (the default) leaves biome size at vanilla.")
        .define("enabled", false);

    public static final ModConfigSpec.IntValue SIZE_MODIFIER = BUILDER
        .comment(
            "Shifts the octave of the noise fields that pick which biome generates where.",
            "0 = vanilla size (the default). Positive = bigger biomes, negative = smaller -",
            "each step is roughly a 2x change in spatial scale, so this range already covers",
            "everywhere from drastically smaller to drastically bigger than vanilla.",
            "Recommended range: -8 to 8."
        )
        .defineInRange("sizeModifier", 0, -8, 8);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BiomeSizeConfig() {
    }
}
