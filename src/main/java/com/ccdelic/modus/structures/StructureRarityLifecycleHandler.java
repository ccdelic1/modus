package com.ccdelic.modus.structures;

import com.ccdelic.modus.Modus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Refreshes {@link StructureRarityRegistry} once per server start, after the structure
 * registry (vanilla + every installed mod's datapack) is fully loaded and available via
 * {@code server.registryAccess()}.
 */
@EventBusSubscriber(modid = Modus.MODID)
public final class StructureRarityLifecycleHandler {
    private StructureRarityLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        StructureRarityRegistry.refresh(event.getServer().registryAccess());
    }
}
