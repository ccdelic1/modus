package com.ccdelic.modus.structures;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.ccdelic.modus.Modus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Discovers every structure (vanilla and modded) from the live {@code Registries.STRUCTURE}
 * registry at server start and gives each one its own config file under
 * {@code config/Modus/structureOptions/}: {@code vanilla/<path>.toml} for
 * {@code minecraft:*} structures, {@code modded/<Mod Display Name>/<path>.toml} otherwise.
 * <p>
 * Structures can't use {@link com.ccdelic.modus.config.OreConfig}'s {@code ModConfigSpec}
 * approach because - unlike ores - they aren't a fixed set known at mod-construction time:
 * they're only known once a datapack (built-in or from an arbitrary mod) has loaded, which
 * happens long after {@code registerConfig} calls would need to run. This reads/writes TOML
 * directly with the same underlying library ({@code nightconfig}) NeoForge's own config
 * system uses, so the file format and editing experience still match.
 * <p>
 * See {@code ChunkGeneratorMixin} for how {@link #get(ResourceLocation)} is actually applied
 * during world generation - rarity below 1.0 (and disabling) rejects generation attempts
 * vanilla already decided to make; rarity above 1.0 rolls an extra, independent per-chunk
 * attempt during normal generation. An earlier version of "more common" scaled the
 * structure's placement spacing grid directly, which broke {@code /locate} (and would have
 * broken anything else relying on that grid being calibrated to vanilla's normal density) -
 * confirmed by an actual watchdog crash during testing - so that approach was abandoned in
 * favor of the independent-roll design, which never touches the grid at all. See
 * {@link #warnAboutUnsupportedRarityIncreases} for a corollary of that same decision:
 * concentric-rings-placed structures (vanilla: only {@code minecraft:stronghold}) can't use
 * the independent-roll mechanism either, so rarity {@literal >} 1.0 has no effect for them.
 */
public final class StructureRarityRegistry {
    public static final double MIN_RARITY = 0.1;
    public static final double MAX_RARITY = 10.0;
    public static final double DEFAULT_RARITY = 1.0;

    private static volatile Map<ResourceLocation, Entry> structureEntries = Map.of();
    private static boolean authoritativeRefreshHasRun = false;

    /**
     * Every known structure that {@code ChunkGeneratorMixin}'s "extra attempts" mechanism
     * can't help - i.e. anything NOT placed via {@link RandomSpreadStructurePlacement} (see
     * {@link #warnAboutUnsupportedRarityIncreases}). Rebuilt on every {@link #doRefresh}
     * alongside {@link #structureEntries}, and exposed via {@link #supportsRarityIncrease} so
     * {@code StructureRarityScreen} can warn a player in-game, at the moment they type a
     * rarity above 1.0 for one of these structures, instead of the only feedback being a
     * server-log {@code WARN} most players never see.
     */
    private static volatile Set<ResourceLocation> unsupportedForRarityIncrease = Set.of();

    private StructureRarityRegistry() {
    }

    public static Entry get(ResourceLocation structureId) {
        Entry entry = structureEntries.get(structureId);
        return entry != null ? entry : Entry.DEFAULT;
    }

    /**
     * Whether setting this structure's rarity above 1.0 would actually do anything. {@code
     * false} for a structure not placed via {@link RandomSpreadStructurePlacement} (vanilla:
     * concentric-rings placements like {@code minecraft:stronghold}) - see
     * {@link #warnAboutUnsupportedRarityIncreases} for why. Unknown structures (not yet seen
     * by any {@link #refresh}/{@link #refreshSpeculative}) default to {@code true} rather than
     * warning about something not even listed yet.
     */
    public static boolean supportsRarityIncrease(ResourceLocation structureId) {
        return !unsupportedForRarityIncrease.contains(structureId);
    }

    /**
     * Every structure known as of the last {@link #refresh}, for {@code
     * com.ccdelic.modus.client.StructureRarityScreen} to list. Empty until a world (or
     * server) has loaded at least once this session, since that's what populates the
     * structure registry this reads from in the first place.
     */
    public static Map<ResourceLocation, Entry> allEntries() {
        return structureEntries;
    }

    /**
     * Persists a single structure's entry back to its TOML file and updates the in-memory
     * snapshot, for {@code com.ccdelic.modus.client.StructureRarityScreen} to call when the
     * player commits a value (a checkbox toggle, a text field losing focus, or the screen
     * closing - see {@link #applyInMemory} for the cheap per-keystroke half of that same
     * screen's editing). Safe to call from the render thread while the server thread
     * concurrently reads {@link #get} - same volatile-snapshot-swap pattern as
     * {@link #refresh}, needed for the same reason (this can run on a singleplayer world,
     * where the "client" and "the world's own server" share the same running game but not
     * the same thread).
     */
    public static synchronized void save(ResourceLocation structureId, Entry entry) {
        Path baseDir = FMLPaths.CONFIGDIR.get().resolve(Modus.CONFIG_FOLDER).resolve("structureOptions");
        Path file = resolveFile(baseDir, structureId);

        try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
            if (Files.exists(file)) {
                config.load();
            }
            config.set("enabled", entry.enabled());
            config.set("rarity", entry.rarity());
            config.save();
        } catch (Exception e) {
            Modus.LOGGER.error("Modus: failed to save structure config for {} at {}", structureId, file, e);
            return;
        }

        applyInMemory(structureId, entry);
    }

    /**
     * Updates only the in-memory snapshot - no disk I/O - so a live edit takes effect
     * immediately for world generation (and the screen's own display) without paying for a
     * file write on every keystroke. {@code com.ccdelic.modus.client.StructureRarityScreen}
     * calls this from its per-keystroke responder; the actual file write is deferred to
     * {@link #save}, called only when the edit is committed (field unfocus, checkbox toggle,
     * or screen close). {@code CommentedFileConfig}'s {@code sync()} option flushes to disk on
     * every {@code save()} call, so typing e.g. "2.50" digit by digit previously meant four
     * full file-open/write/sync/close cycles for one intended edit; this bounds disk writes to
     * once per commit instead of once per keystroke.
     */
    public static synchronized void applyInMemory(ResourceLocation structureId, Entry entry) {
        Map<ResourceLocation, Entry> updated = new HashMap<>(structureEntries);
        updated.put(structureId, entry);
        structureEntries = Map.copyOf(updated);
    }

    /**
     * Rebuilds the whole snapshot from a real server's registry access - called from
     * {@code StructureRarityLifecycleHandler} on every server start (singleplayer or
     * dedicated). This is the authoritative source: it reflects the actual data packs of the
     * world being played, and once it has run at least once this session,
     * {@link #refreshSpeculative} results are discarded rather than allowed to overwrite it.
     */
    public static synchronized void refresh(RegistryAccess registryAccess) {
        authoritativeRefreshHasRun = true;
        doRefresh(registryAccess);
    }

    /**
     * Same rebuild, but from {@code com.ccdelic.modus.client.WorldgenConfigBootstrap}'s
     * stand-in registry access (built from default settings before any world exists), purely
     * so the config screen isn't empty before a player has loaded anything. Because the
     * bootstrap runs on a background thread, it could in principle finish AFTER a real
     * server's {@link #refresh} (e.g. quickplay auto-loading a world at launch) - and its
     * default-datapack view could be missing structures that world's own data packs add - so
     * a speculative result never overwrites an authoritative one, only ever an empty or
     * older-speculative snapshot.
     */
    public static synchronized void refreshSpeculative(RegistryAccess registryAccess) {
        if (authoritativeRefreshHasRun) {
            Modus.LOGGER.debug(
                "Modus: skipping speculative structure registry bootstrap; a real server already provided authoritative data."
            );
            return;
        }
        doRefresh(registryAccess);
    }

    private static void doRefresh(RegistryAccess registryAccess) {
        Path baseDir = FMLPaths.CONFIGDIR.get().resolve(Modus.CONFIG_FOLDER).resolve("structureOptions");

        Map<ResourceLocation, Entry> newStructureEntries = new HashMap<>();
        registryAccess.lookupOrThrow(Registries.STRUCTURE).listElements().forEach(holder -> {
            ResourceLocation id = holder.key().location();
            Path file = resolveFile(baseDir, id);
            newStructureEntries.put(id, loadOrCreate(file, id));
        });

        structureEntries = Map.copyOf(newStructureEntries);
        Modus.LOGGER.info("Modus: loaded structure rarity config for {} structures", newStructureEntries.size());

        warnAboutUnsupportedRarityIncreases(registryAccess, newStructureEntries);
    }

    /**
     * {@code ChunkGeneratorMixin}'s "extra attempts" mechanism (rarity {@literal >} 1.0) only
     * works for structures placed via {@link RandomSpreadStructurePlacement}'s per-chunk grid
     * roll - that is what it piggybacks an extra roll onto. Structures placed via
     * {@link net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement}
     * (vanilla: only {@code minecraft:stronghold}) instead resolve to a small, fixed set of
     * candidate chunks computed once per world; there's no natural per-chunk "extra roll" to
     * add for those, and deliberately not implemented given increasing their ring size or
     * count would - like the abandoned spacing-scaling approach documented in
     * {@code ChunkGeneratorMixin} - directly perturb the same placement machinery
     * {@code /locate} depends on. Disabling and rarity {@literal <} 1.0 both still work for
     * these structures, since the veto mixin operates inside {@code tryGenerateStructure}
     * regardless of how the candidate chunk was selected.
     * <p>
     * Populates {@link #unsupportedForRarityIncrease} with every non-random-spread structure
     * (regardless of its CURRENT rarity value, so the config screen knows in advance which
     * structures a rarity increase would be wasted on, not just the ones already set above
     * 1.0), then logs a {@code WARN} listing only those CURRENTLY set above 1.0 - the narrower,
     * more actionable subset for a server-log reader.
     */
    private static void warnAboutUnsupportedRarityIncreases(RegistryAccess registryAccess, Map<ResourceLocation, Entry> entries) {
        Set<ResourceLocation> randomSpreadStructures = new HashSet<>();
        registryAccess.lookupOrThrow(Registries.STRUCTURE_SET).listElements().forEach(holder -> {
            StructureSet set = holder.value();
            if (set.placement() instanceof RandomSpreadStructurePlacement) {
                for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                    entry.structure().unwrapKey().ifPresent(key -> randomSpreadStructures.add(key.location()));
                }
            }
        });

        unsupportedForRarityIncrease = entries.keySet().stream()
            .filter(id -> !randomSpreadStructures.contains(id))
            .collect(Collectors.toUnmodifiableSet());

        List<ResourceLocation> unsupported = entries.entrySet().stream()
            .filter(e -> e.getValue().rarity() > DEFAULT_RARITY)
            .map(Map.Entry::getKey)
            .filter(id -> !randomSpreadStructures.contains(id))
            .sorted()
            .toList();

        if (!unsupported.isEmpty()) {
            Modus.LOGGER.warn(
                "Modus: rarity > 1.0 has no effect for these structures because they aren't placed via a "
                    + "random-spread grid (e.g. concentric-rings placements like strongholds have no per-chunk "
                    + "'extra attempt' to piggyback on): {}. Disabling and rarity < 1.0 are unaffected by this.",
                unsupported
            );
        }
    }

    private static Path resolveFile(Path baseDir, ResourceLocation id) {
        String path = sanitizePathSegments(id.getPath());
        if (id.getNamespace().equals("minecraft")) {
            return baseDir.resolve("vanilla").resolve(path + ".toml");
        }
        return baseDir.resolve("modded").resolve(modFolderNameFor(id.getNamespace())).resolve(path + ".toml");
    }

    /**
     * Neutralizes any {@code "."} or {@code ".."} path segment in a structure id's path
     * before it's resolved into a file path. {@link ResourceLocation}'s allowed path
     * characters ({@code [a-z0-9/._-]}) include {@code '.'}, so - despite what it might look
     * like at a glance - a literal {@code ".."} segment IS a valid {@code ResourceLocation}
     * path and isn't rejected anywhere upstream; only a full segment that IS exactly {@code
     * "."} or {@code ".."} is special to the filesystem (a segment merely containing dots,
     * like {@code "foo..bar"}, is just an ordinary literal name and is left untouched here).
     * Mods are trusted code, so this is a defense-in-depth measure against a buggy or
     * malicious structure id trying to write outside {@code config/Modus/structureOptions/}.
     */
    private static String sanitizePathSegments(String path) {
        String[] segments = path.split("/", -1);
        boolean changed = false;
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].equals(".") || segments[i].equals("..")) {
                segments[i] = segments[i].replace('.', '_');
                changed = true;
            }
        }
        if (changed) {
            Modus.LOGGER.warn("Modus: structure id path \"{}\" contained a '.' or '..' segment; sanitized to avoid writing outside its config directory.", path);
            return String.join("/", segments);
        }
        return path;
    }

    private static String modFolderNameFor(String namespace) {
        return ModList.get().getModContainerById(namespace)
            .map(container -> sanitizeForPath(container.getModInfo().getDisplayName()))
            .orElseGet(() -> sanitizeForPath(namespace));
    }

    private static String sanitizeForPath(String name) {
        String sanitized = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        // Windows also disallows a path segment ending in a trailing '.' or space, in any mix
        // (e.g. "Foo Mod." or "Foo Mod. ", where trimming the space alone would re-expose a
        // trailing dot) - trim() above only strips a plain trailing space, so a mod display
        // name ending in a period would otherwise still produce an invalid Windows path
        // segment. One regex strips any trailing run of dots/whitespace together, rather than
        // needing multiple alternating passes to catch both orders.
        sanitized = sanitized.replaceAll("[.\\s]+$", "");
        return sanitized.isEmpty() ? "unknown" : sanitized;
    }

    private static Entry loadOrCreate(Path file, ResourceLocation id) {
        try {
            boolean existed = Files.exists(file);
            Files.createDirectories(file.getParent());

            try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
                if (existed) {
                    config.load();
                } else {
                    config.set("enabled", true);
                    config.setComment("enabled", " Whether " + id + " is allowed to generate at all.");
                    config.set("rarity", DEFAULT_RARITY);
                    config.setComment(
                        "rarity",
                        " Rarity multiplier for " + id + ".\n"
                            + " 1.0 = vanilla rate, 0.1 = 10x less common, 10.0 = 10x more common.\n"
                            + " Range: " + MIN_RARITY + " ~ " + MAX_RARITY
                    );
                    config.save();
                }

                boolean enabled = config.getOrElse("enabled", true);
                double rawRarity = readDouble(config, "rarity", DEFAULT_RARITY);
                double rarity = clampRarity(rawRarity);
                if (rarity != rawRarity) {
                    config.set("rarity", rarity);
                    config.save();
                }

                return new Entry(enabled, rarity);
            }
        } catch (Exception e) {
            Modus.LOGGER.error("Modus: failed to load structure config for {} at {}; using defaults.", id, file, e);
            return Entry.DEFAULT;
        }
    }

    /**
     * Reads {@code key} as a {@code double}, falling back to {@code fallback} both when the
     * key is absent and when it holds a value that isn't a number at all - e.g. a hand-edited
     * TOML file with {@code rarity = "hello"}. Nightconfig's {@code get} is generically typed,
     * so assigning its result directly to a {@code Number} would insert an unchecked cast that
     * throws {@code ClassCastException} for a non-numeric value; that exception would surface
     * to {@link #loadOrCreate}'s outer catch block as a generic "failed to load" message with
     * no indication a type mismatch (rather than, say, a missing file or malformed TOML) was
     * the actual cause. Checking with {@code instanceof} first avoids the exception entirely
     * and logs specifically what was wrong.
     */
    private static double readDouble(CommentedFileConfig config, String key, double fallback) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            Modus.LOGGER.warn(
                "Modus: expected a number for '{}' but found \"{}\" ({}); using default {}.",
                key, value, value.getClass().getSimpleName(), fallback
            );
        }
        return fallback;
    }

    private static double clampRarity(double rarity) {
        if (Double.isNaN(rarity) || Double.isInfinite(rarity)) {
            return DEFAULT_RARITY;
        }
        return Math.max(MIN_RARITY, Math.min(MAX_RARITY, rarity));
    }

    public record Entry(boolean enabled, double rarity) {
        public static final Entry DEFAULT = new Entry(true, DEFAULT_RARITY);
    }
}
