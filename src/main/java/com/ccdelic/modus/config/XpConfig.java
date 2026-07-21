package com.ccdelic.modus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Single config file (config/Modus/XP/xp.toml) controlling a global experience gain
 * multiplier, from 0.1 (a tenth of vanilla XP) to 10.0 (10x). 1.0 is vanilla's rate. See
 * {@link com.ccdelic.modus.xp.XpGainHandler} for where this is applied.
 */
public final class XpConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Master switch for the XP multiplier. false (the default) leaves players at vanilla (1.0x) experience.")
        .define("enabled", false);

    public static final ModConfigSpec.DoubleValue MULTIPLIER = BUILDER
        .comment(
            "Multiplier applied to experience points gained by players, from every source",
            "(mining, mob kills, smelting, fishing, breeding, bottles o' enchanting, etc).",
            "1.0 = vanilla rate, 0.1 = a tenth of vanilla XP, 10.0 = 10x XP. Only applies to",
            "XP gains, never to XP losses (e.g. from commands or death). Values below 1.0",
            "still work correctly even for sources that only ever grant XP one point at a",
            "time - see XpGainHandler for the fractional-remainder rounding that makes that",
            "true down to this range's floor."
        )
        .defineInRange("multiplier", 1.0, 0.1, 10.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private XpConfig() {
    }
}
