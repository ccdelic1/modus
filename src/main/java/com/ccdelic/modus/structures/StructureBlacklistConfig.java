package com.ccdelic.modus.structures;

import com.ccdelic.modus.Modus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Single config file (config/Modus/structureOptions/blacklist.toml) listing block-for-block
 * replacements applied to every structure as it generates - unlike the rest of
 * {@code structureOptions/}, this isn't per-structure: one blacklisted block (e.g.
 * {@code minecraft:bookshelf}) is replaced everywhere it would have generated, in every
 * structure, vanilla or modded. See {@code StructureBlacklistMixin} for where this is
 * actually applied.
 * <p>
 * Unlike {@link com.ccdelic.modus.config.OreConfig} (one file per ore, since ores are a
 * fixed set) and {@link StructureRarityRegistry} (dynamically discovered structures, using
 * raw TOML because they aren't known until a world's registries are loaded), this can safely
 * use a normal {@link ModConfigSpec}: blocks - unlike structures - are a static registry
 * ({@link BuiltInRegistries#BLOCK}) fully populated well before any config value is ever
 * read, so there's no timing problem to work around.
 * <p>
 * Each entry is parsed once into a {@code Block -> Block} lookup and cached, rather than
 * re-parsed on every block a structure places (a legitimately hot path - a single village
 * house can place hundreds of blocks). The cache is rebuilt from
 * {@code StructureBlacklistLifecycleHandler} whenever NeoForge reports the config loaded or
 * changed, since {@code ModConfigEvent.Reloading} can - per its own documentation - fire on
 * any thread, so the swap uses the same volatile-snapshot pattern as
 * {@link StructureRarityRegistry} rather than mutating the map in place.
 */
public final class StructureBlacklistConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Master switch for structure block replacement. false (the default) leaves structures unmodified.")
        .define("enabled", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST = BUILDER
        .comment(
            "Block-for-block replacements applied to every structure as it generates (villages,",
            "temples, modded structures, etc.) - see the mod description for exactly which",
            "structures this can and can't reach.",
            "Each entry replaces one block with another wherever it would have generated. Format:",
            "\"<source_block> <replacement_block>\", both full block IDs, separated by one space.",
            "Example: \"minecraft:bookshelf minecraft:oak_log\" replaces every bookshelf with an",
            "oak log. Unknown block IDs and malformed entries are ignored (logged as a warning) -",
            "they never prevent the rest of the list, or structure generation itself, from working.",
            "Replacement is one hop only: if both \"a b\" and \"b c\" are listed, blocks of type a",
            "become b, not c - chained/cyclic entries can't cause a loop."
        )
        .defineListAllowEmpty("blacklist", List.of(), () -> "minecraft:bookshelf minecraft:oak_log", entry -> entry instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile Map<Block, Block> resolved = Map.of();

    private StructureBlacklistConfig() {
    }

    /**
     * Returns the block that should replace {@code source}, or {@code null} if it isn't
     * blacklisted (or replacement is disabled). Safe to call for every block a structure
     * places - a single volatile read plus one hashmap lookup regardless of list size.
     */
    public static Block replacementFor(Block source) {
        return ENABLED.get() ? resolved.get(source) : null;
    }

    /**
     * Fast "is there anything to do at all" gate, meant to be checked FIRST on genuinely hot
     * paths (like {@code WorldGenRegionBlacklistMixin}'s per-{@code setBlock} hook, which runs
     * for every block placed anywhere in world generation). When replacement is off or the
     * list is empty - the default - this is one config read plus one volatile map read, so the
     * hook can bail out before doing any of the more expensive per-block work (a
     * {@link ThreadLocal} lookup, in that mixin's case). {@link #replacementFor} already
     * covers correctness on its own; this only exists to keep the common no-op case cheap.
     */
    public static boolean hasReplacements() {
        return ENABLED.get() && !resolved.isEmpty();
    }

    public static synchronized void refresh() {
        Map<Block, Block> newResolved = new HashMap<>();
        for (String rawEntry : BLACKLIST.get()) {
            parseEntry(rawEntry).ifPresent(pair -> {
                if (newResolved.containsKey(pair.source()) && newResolved.get(pair.source()) != pair.target()) {
                    Modus.LOGGER.warn(
                        "Modus: structure blacklist entry \"{}\" overrides an earlier replacement for {}; using the later one.",
                        rawEntry, BuiltInRegistries.BLOCK.getKey(pair.source())
                    );
                }
                newResolved.put(pair.source(), pair.target());
            });
        }

        resolved = Map.copyOf(newResolved);
        Modus.LOGGER.info("Modus: loaded structure block blacklist with {} replacement(s)", newResolved.size());
    }

    private static Optional<BlockPair> parseEntry(String rawEntry) {
        String[] parts = rawEntry.trim().split("\\s+");
        if (parts.length != 2) {
            Modus.LOGGER.warn(
                "Modus: ignoring malformed structure blacklist entry \"{}\" - expected exactly two block IDs separated by whitespace.",
                rawEntry
            );
            return Optional.empty();
        }

        Block source = resolveBlock(rawEntry, parts[0]);
        Block target = resolveBlock(rawEntry, parts[1]);
        if (source == null || target == null) {
            return Optional.empty();
        }

        return Optional.of(new BlockPair(source, target));
    }

    private static Block resolveBlock(String rawEntry, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Block block = location == null ? null : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        if (block == null) {
            Modus.LOGGER.warn("Modus: ignoring structure blacklist entry \"{}\" - unknown block id \"{}\".", rawEntry, id);
        }
        return block;
    }

    private record BlockPair(Block source, Block target) {
    }
}
