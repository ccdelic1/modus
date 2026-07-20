package com.ccdelic.modus.structures;

import com.ccdelic.modus.Modus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Rebuilds {@link StructureBlacklistConfig}'s resolved block map whenever NeoForge reports
 * its config file loaded or changed. Unlike {@link StructureRarityLifecycleHandler} (which
 * waits for {@code ServerAboutToStartEvent} because it needs a server's dynamic structure
 * registry), the blacklist only needs the static block registry, which is available well
 * before that - {@code ModConfigEvent} is the correct, idiomatic signal for a plain
 * {@code ModConfigSpec} config instead.
 */
@EventBusSubscriber(modid = Modus.MODID)
public final class StructureBlacklistLifecycleHandler {
    private StructureBlacklistLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        refreshIfOurConfig(event);
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        refreshIfOurConfig(event);
    }

    private static void refreshIfOurConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == StructureBlacklistConfig.SPEC) {
            StructureBlacklistConfig.refresh();
        }
    }
}
