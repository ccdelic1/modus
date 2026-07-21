package com.ccdelic.modus.client;

import com.ccdelic.modus.Modus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Kicks off {@link WorldgenConfigBootstrap} once, during client startup, so both the
 * structure-rarity and modded-ore config screens are populated before a world is ever loaded.
 * {@code value = Dist.CLIENT} keeps this - and by extension the client-only classes it
 * references - from being scanned on a dedicated server, where {@code FMLClientSetupEvent}
 * never fires anyway but the class itself still shouldn't be touched.
 */
@EventBusSubscriber(modid = Modus.MODID, value = Dist.CLIENT)
public final class WorldgenConfigBootstrapHandler {
    private WorldgenConfigBootstrapHandler() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        WorldgenConfigBootstrap.runInBackground();
    }
}
