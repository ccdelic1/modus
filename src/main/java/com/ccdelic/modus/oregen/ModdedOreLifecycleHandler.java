package com.ccdelic.modus.oregen;

import com.ccdelic.modus.Modus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Refreshes {@link ModdedOreRegistry} once per server start, after every installed mod's ore
 * placed features are loaded and available via {@code server.registryAccess()} - the same
 * timing and reasoning as {@code StructureRarityLifecycleHandler}.
 */
@EventBusSubscriber(modid = Modus.MODID)
public final class ModdedOreLifecycleHandler {
    private ModdedOreLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ModdedOreRegistry.refresh(event.getServer().registryAccess());
    }
}
