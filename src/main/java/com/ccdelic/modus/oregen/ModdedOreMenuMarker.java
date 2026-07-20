package com.ccdelic.modus.oregen;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * A near-empty {@link ModConfigSpec} registered purely so "Modded Ore Options" gets its own
 * entry in Modus's in-game config screen list, alongside the vanilla per-ore sections,
 * "Structure Rarity Options", and the rest. Same trick, and same reason, as
 * {@code com.ccdelic.modus.structures.StructureRarityMenuMarker}: modded ores are discovered
 * dynamically after data packs load, so they can't be a real {@code ModConfigSpec}, and
 * NeoForge's config screen only lists registered {@code ModConfig}s - plus a truly empty spec
 * is silently dropped by {@code ModContainer#registerConfig}, hence the one inert field.
 * {@code ModusClient#sectionScreenFor} recognizes this spec and opens
 * {@code com.ccdelic.modus.client.ModdedOreScreen} instead of the pointless section screen it
 * would otherwise lead to.
 */
public final class ModdedOreMenuMarker {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue PLACEHOLDER = BUILDER
        .comment(
            "This file only exists to give \"Modded Ore Options\" its own entry in the in-game",
            "config screen list. It has no effect either way - the actual per-ore multipliers",
            "live under oreOptions/modded/<Mod Name>/."
        )
        .define("doNotEditThisFile", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ModdedOreMenuMarker() {
    }
}
