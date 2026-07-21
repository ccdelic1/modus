package com.ccdelic.modus.oregen;

import com.ccdelic.modus.Modus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Refreshes {@link ModdedOreRegistry} once per server start, after every installed mod's ore
 * placed features are loaded and available via {@code server.registryAccess()} - the same
 * timing and reasoning as {@code StructureRarityLifecycleHandler}, including why this catches,
 * logs, and rethrows rather than letting an exception here pass through unattributed - see
 * that class's javadoc.
 */
@EventBusSubscriber(modid = Modus.MODID)
public final class ModdedOreLifecycleHandler {
    private ModdedOreLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            ModdedOreRegistry.refresh(event.getServer().registryAccess());
        } catch (RuntimeException e) {
            Modus.LOGGER.error(
                "Modus: ModdedOreRegistry failed to refresh during server startup; modded ore config will not "
                    + "reflect this world's installed ore mods. Rethrowing so server startup still fails as it "
                    + "would have without this handler.",
                e
            );
            throw e;
        }
    }
}
