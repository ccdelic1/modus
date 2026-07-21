package com.ccdelic.modus.mobs;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.ccdelic.modus.Modus;
import com.ccdelic.modus.config.MobConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Discovers every {@link MobCategory} value that isn't one of vanilla's own - i.e. one another
 * installed mod registered via {@code MobCategory}'s NeoForge {@code IExtensibleEnum} support -
 * and gives the whole group one shared config file, {@code config/Modus/mobOptions/modded.toml},
 * with one integer entry per detected category.
 * <p>
 * Unlike {@code ModdedOreRegistry} / {@code StructureRarityRegistry}, this needs no "before any
 * world exists" bootstrap and no world-load refresh: ores and structures come from data packs,
 * which only resolve once a real (or speculative) world load happens, but {@link MobCategory}
 * is a plain enum-like registry that every installed mod finishes extending during its own
 * construction, at mod-loading time - well before the main menu even appears, and the same on
 * every subsequent world load within the session. So {@link #ensureDiscovered()} only needs to
 * run once, ever, whenever something first asks for this registry's contents (the "Mob Spawn
 * Caps" config screen, or {@link MobSpawnCapHandler} at the first server start).
 * <p>
 * Shares {@link MobConfig#ENABLED} as its own master switch rather than having a second one -
 * from a player's perspective "mob spawn cap overrides" is one feature, whether the category
 * being capped is vanilla or modded.
 */
public final class ModdedMobCategoryRegistry {
    public static final int MIN_CAP = 1;
    public static final int MAX_CAP = 350;

    private static final Set<MobCategory> KNOWN_VANILLA_CATEGORIES = Set.of(
        MobCategory.MONSTER,
        MobCategory.CREATURE,
        MobCategory.AMBIENT,
        MobCategory.AXOLOTLS,
        MobCategory.UNDERGROUND_WATER_CREATURE,
        MobCategory.WATER_CREATURE,
        MobCategory.WATER_AMBIENT,
        MobCategory.MISC
    );

    private static volatile Map<MobCategory, Entry> entries = Map.of();
    private static boolean discovered = false;

    private ModdedMobCategoryRegistry() {
    }

    /** Every detected modded spawn category and its currently configured cap. */
    public static Map<MobCategory, Entry> allEntries() {
        return entries;
    }

    /** The configured cap for {@code category}, or {@code null} if it isn't a detected modded category. */
    public static Entry get(MobCategory category) {
        return entries.get(category);
    }

    /**
     * Runs the actual file scan the first time anything calls this in a session; every later
     * call is a no-op, since the set of registered {@link MobCategory} values never changes
     * once mod loading has finished (see the class javadoc for why that makes a one-time
     * discovery correct here, unlike the world-load-dependent registries this mirrors).
     */
    public static synchronized void ensureDiscovered() {
        if (discovered) {
            return;
        }
        discovered = true;
        doDiscover();
    }

    private static void doDiscover() {
        List<MobCategory> modded = new ArrayList<>();
        for (MobCategory category : MobCategory.values()) {
            if (!KNOWN_VANILLA_CATEGORIES.contains(category)) {
                modded.add(category);
            }
        }
        if (modded.isEmpty()) {
            Modus.LOGGER.debug("Modus: no modded mob spawn categories detected.");
            return;
        }

        Path file = configFile();
        Map<MobCategory, Entry> discoveredEntries = new LinkedHashMap<>();
        try {
            Files.createDirectories(file.getParent());
            try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
                if (Files.exists(file)) {
                    config.load();
                }
                for (MobCategory category : modded) {
                    String key = category.getSerializedName();
                    int vanillaDefault = MobSpawnCapHandler.vanillaMaxFor(category);
                    if (!config.contains(key)) {
                        config.set(key, vanillaDefault);
                        config.setComment(
                            key,
                            " Maximum " + key + " category mobs per chunk (a modded spawn category Modus auto-detected).\n"
                                + " Only applied while mobOptions/mobs.toml's \"enabled\" (shared with the vanilla categories) is true.\n"
                                + " Default: " + vanillaDefault + ". Range: " + MIN_CAP + " - " + MAX_CAP + "."
                        );
                    }
                    int max = clamp(readInt(config, key, vanillaDefault), MIN_CAP, MAX_CAP);
                    discoveredEntries.put(category, new Entry(max));
                }
                config.save();
            }
        } catch (Exception e) {
            Modus.LOGGER.error(
                "Modus: failed to load modded mob spawn category config at {}; modded categories will use their own defaults this session.",
                file, e
            );
            Map<MobCategory, Entry> fallback = new LinkedHashMap<>();
            for (MobCategory category : modded) {
                fallback.put(category, new Entry(MobSpawnCapHandler.vanillaMaxFor(category)));
            }
            entries = Map.copyOf(fallback);
            return;
        }

        entries = Map.copyOf(discoveredEntries);
        Modus.LOGGER.info(
            "Modus: detected {} modded mob spawn categor{}: {}",
            discoveredEntries.size(), discoveredEntries.size() == 1 ? "y" : "ies",
            discoveredEntries.keySet().stream().map(MobCategory::getSerializedName).sorted().toList()
        );
    }

    /** In-memory only, for the config screen's per-keystroke live preview - see {@code ModdedOreRegistry#applyInMemory} for why. */
    public static synchronized void applyInMemory(MobCategory category, Entry entry) {
        Map<MobCategory, Entry> updated = new LinkedHashMap<>(entries);
        updated.put(category, entry);
        entries = Map.copyOf(updated);
    }

    /** Persists {@code entry} to disk and updates the in-memory snapshot. */
    public static synchronized void save(MobCategory category, Entry entry) {
        Path file = configFile();
        try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
            if (Files.exists(file)) {
                config.load();
            }
            config.set(category.getSerializedName(), entry.max());
            config.save();
        } catch (Exception e) {
            Modus.LOGGER.error("Modus: failed to save modded mob spawn category config for {}.", category.getSerializedName(), e);
            return;
        }
        applyInMemory(category, entry);
    }

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve(Modus.CONFIG_FOLDER).resolve("mobOptions").resolve("modded.toml");
    }

    private static int readInt(CommentedFileConfig config, String key, int fallback) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            Modus.LOGGER.warn("Modus: expected a number for '{}' but found \"{}\" ({}); using default {}.", key, value, value.getClass().getSimpleName(), fallback);
        }
        return fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Entry(int max) {
    }
}
