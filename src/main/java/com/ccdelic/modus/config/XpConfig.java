package com.ccdelic.modus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Single config file (config/Modus/XP/xp.toml) controlling a global experience gain
 * multiplier. 1.0 is vanilla's rate; 0.5 halves XP gained, 10.0 grants 10x. See
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
            "1.0 = vanilla rate, 0.5 = half XP, 10.0 = 10x XP. Only applies to XP gains,",
            "never to XP losses (e.g. from commands or death)."
        )
        .defineInRange("multiplier", 1.0, 0.5, 10.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private XpConfig() {
    }
}
