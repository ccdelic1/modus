package com.ccdelic.modus.client;

import com.mojang.datafixers.util.Pair;
import com.ccdelic.modus.Modus;
import com.ccdelic.modus.oregen.ModdedOreRegistry;
import com.ccdelic.modus.structures.StructureRarityRegistry;
import net.minecraft.Util;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.world.level.WorldDataConfiguration;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

/**
 * Populates both {@link StructureRarityRegistry} and {@link ModdedOreRegistry} before any
 * world exists, so their config-screen sections ("Structure Rarity Options" and "Modded Ore
 * Options") aren't empty the first time a player opens them - normally those registries only
 * get filled once a real server (singleplayer or dedicated) starts, since that's what loads
 * the data packs a structure/ore list comes from.
 * <p>
 * This replicates the same mechanism the vanilla "Create World" screen ({@code WorldOpenFlows})
 * uses to resolve datapack registries before a world is created -
 * {@link ServerPacksSource#createVanillaTrustedRepository()} builds a pack repository from
 * vanilla plus every installed mod's bundled data (via NeoForge's {@code ResourcePackLoader},
 * no specific world needed), which gets fed into the same {@code RegistryLayer.WORLDGEN}
 * loading step {@link WorldLoader#load} itself performs. That one loaded WORLDGEN layer holds
 * {@code Registries.STRUCTURE}, {@code Registries.STRUCTURE_SET} AND
 * {@code Registries.PLACED_FEATURE}, so a single resolution feeds both registries.
 * <p>
 * Deliberately <em>not</em> calling {@link WorldLoader#load} itself: that method also loads
 * {@code RegistryLayer.DIMENSIONS} and then the full {@code ReloadableServerResources}
 * (recipes, loot tables, tags, advancements, and more) - all real costs with nothing to do
 * with worldgen config. Stopping after the {@code WORLDGEN} layer means this only pays for
 * parsing worldgen-related data pack content (biomes, structures, features, density
 * functions, and their dependencies), not everything else a world load touches.
 * <p>
 * Runs once, in the background, from {@link WorldgenConfigBootstrapHandler} at client startup
 * - never blocks the render thread. If a real server starts before this finishes (or at all),
 * that real, authoritative {@code refresh} always wins, since it runs later and both
 * registries discard a speculative result once an authoritative one has landed; this is only
 * ever a placeholder for the gap before that happens.
 * <p>
 * A config screen open at the exact moment this (or a later authoritative {@code refresh})
 * lands can see its list of rows change the next time it rebuilds (e.g. on a window resize,
 * which re-invokes {@code addOptions()}) - cosmetic only, never a torn or partially-updated
 * read: both registries publish one complete, immutable snapshot per update (see
 * {@code ModdedOreRegistry}'s {@code Snapshot}/{@code StructureRarityRegistry}'s
 * {@code structureEntries}), and each screen's {@code addOptions()} call captures
 * {@code allEntries()} into a single local exactly once before iterating it, so there is no
 * window during which that one rebuild could observe a map becoming empty or otherwise
 * changing mid-iteration - only ever a complete, consistent snapshot, possibly a newer one
 * than the previous rebuild saw. Confirmed by inspection; not something a code change can
 * meaningfully improve on without freezing the screen to stale data, which would work against
 * the screens' whole point of showing live, up-to-date registry contents.
 * <p>
 * Uses {@link DataPackRegistriesHooks}, a NeoForge-internal API ({@code @ApiStatus.Internal})
 * not meant for mod use and not guaranteed stable across versions - accepted here the same
 * way this mod already accepts that risk for its Mixins, since there's no public equivalent
 * for "give me just the worldgen datapack registries, resolved, without a real world."
 * A future NeoForge version removing or changing this method's signature would surface as a
 * compile error when Modus is updated against it - not a silent runtime failure for end
 * users - since it's called here as ordinary Java source, not reflectively; {@link
 * #runInBackground}'s {@code catch (Exception e)} exists for this method failing at runtime
 * for some other reason (e.g. a malformed data pack), not for that kind of breakage.
 */
public final class WorldgenConfigBootstrap {
    private WorldgenConfigBootstrap() {
    }

    public static void runInBackground() {
        Util.backgroundExecutor().execute(() -> {
            try {
                run();
            } catch (Exception e) {
                Modus.LOGGER.warn(
                    "Modus: speculative worldgen-config bootstrap failed; structure rarity and modded ore options "
                        + "will only be available in the config screen after loading a world.",
                    e
                );
            }
        });
    }

    private static void run() throws Exception {
        PackRepository packRepository = ServerPacksSource.createVanillaTrustedRepository();
        WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, WorldDataConfiguration.DEFAULT, false, false);
        Pair<WorldDataConfiguration, CloseableResourceManager> pair = packConfig.createResourceManager();

        try (CloseableResourceManager resourceManager = pair.getSecond()) {
            LayeredRegistryAccess<RegistryLayer> base = RegistryLayer.createRegistryAccess();
            RegistryAccess.Frozen loadedWorldgen = RegistryDataLoader.load(
                resourceManager, base.getAccessForLoading(RegistryLayer.WORLDGEN), DataPackRegistriesHooks.getDataPackRegistries()
            );
            LayeredRegistryAccess<RegistryLayer> withWorldgen = base.replaceFrom(RegistryLayer.WORLDGEN, loadedWorldgen);
            RegistryAccess.Frozen worldgenRegistryAccess = withWorldgen.getAccessForLoading(RegistryLayer.DIMENSIONS);

            StructureRarityRegistry.refreshSpeculative(worldgenRegistryAccess);
            ModdedOreRegistry.refreshSpeculative(worldgenRegistryAccess);
        }
    }
}
