package com.ccdelic.modus.structures;

import com.ccdelic.modus.Modus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Refreshes {@link StructureRarityRegistry} once per server start, after the structure
 * registry (vanilla + every installed mod's datapack) is fully loaded and available via
 * {@code server.registryAccess()}.
 * <p>
 * A server crash report was once observed with "Failed to initialize server" and no root
 * exception preserved anywhere in the report, meaning one of the mod's
 * {@code ServerAboutToStartEvent} handlers (this one, or one of its two siblings) may have
 * thrown - but which one, and why, couldn't be determined after the fact. Catching and
 * logging here (then rethrowing, so server startup still fails exactly as before - this
 * isn't meant to let startup limp along in a half-configured state) means if this ever
 * happens again, Modus's own log will show a clear, attributable stack trace for THIS
 * handler specifically, ahead of whatever swallows the exception further up the chain.
 */
@EventBusSubscriber(modid = Modus.MODID)
public final class StructureRarityLifecycleHandler {
    private StructureRarityLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            StructureRarityRegistry.refresh(event.getServer().registryAccess());
        } catch (RuntimeException e) {
            Modus.LOGGER.error(
                "Modus: StructureRarityRegistry failed to refresh during server startup; structure rarity and the "
                    + "structure block blacklist will not reflect this world's structures. Rethrowing so server "
                    + "startup still fails as it would have without this handler.",
                e
            );
            throw e;
        }
    }
}
