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
 * Uses {@link DataPackRegistriesHooks}, a NeoForge-internal API ({@code @ApiStatus.Internal})
 * not meant for mod use and not guaranteed stable across versions - accepted here the same
 * way this mod already accepts that risk for its Mixins, since there's no public equivalent
 * for "give me just the worldgen datapack registries, resolved, without a real world."
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
