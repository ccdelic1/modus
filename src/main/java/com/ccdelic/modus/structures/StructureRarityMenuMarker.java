package com.ccdelic.modus.structures;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * A near-empty {@link ModConfigSpec}, registered purely so "Structure Rarity Options"
 * appears as its own entry in Modus's config screen list, alongside "Structure Block
 * Blacklist", "XP Settings", and the rest - NeoForge's {@code ConfigurationScreen} builds
 * that list directly from every {@code ModConfig} registered via {@code
 * ModContainer#registerConfig}, and per-structure rarity settings can't be a real one of
 * those (see {@code com.ccdelic.modus.client.StructureRarityScreen}'s javadoc for why).
 * {@code ModusClient#sectionScreenFor} recognizes this specific spec and opens {@code
 * StructureRarityScreen} directly instead of the (real, but otherwise pointless) section
 * screen this would normally lead to - so {@link #PLACEHOLDER} is never actually shown to a
 * player; it exists only because {@code ModContainer#registerConfig}'s own javadoc says
 * plainly that "an empty config spec will be ignored" - confirmed the hard way, by a truly
 * empty spec silently never producing a button at all. One inert field is the cheapest way
 * to make the spec non-empty.
 */
public final class StructureRarityMenuMarker {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue PLACEHOLDER = BUILDER
        .comment(
            "This file only exists to give \"Structure Rarity Options\" its own entry in the",
            "in-game config screen list. It has no effect either way - the actual per-structure",
            "settings live under structureOptions/vanilla/ and structureOptions/modded/<Mod Name>/."
        )
        .define("doNotEditThisFile", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private StructureRarityMenuMarker() {
    }
}
