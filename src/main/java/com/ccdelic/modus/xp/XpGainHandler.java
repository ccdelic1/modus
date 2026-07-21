package com.ccdelic.modus.xp;

import com.ccdelic.modus.Modus;
import com.ccdelic.modus.config.XpConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Scales experience points gained by players by {@link XpConfig#MULTIPLIER}. Hooks
 * {@link PlayerXpEvent.XpChange}, which vanilla's {@code Player#giveExperiencePoints} fires
 * for every raw XP grant regardless of source - orb pickups (mining, mob kills, fishing,
 * breeding, ...) all route through {@code ExperienceOrb#playerTouch} -&gt;
 * {@code giveExperiencePoints}, and direct grants (e.g. furnace smelting XP claimed from
 * the output slot) call it too. This is the single choke point every gain passes through,
 * so hooking it here (and nowhere else - not {@code LivingExperienceDropEvent}, not
 * {@code PlayerXpEvent.PickupXp}) avoids scaling the same gain twice.
 * <p>
 * Deliberately does not scale {@code PlayerXpEvent.LevelChange}: that event also fires as
 * an internal side effect of an already-scaled point gain overflowing into a level-up, so
 * touching it too would double-apply the multiplier to level-based transitions.
 * <p>
 * Runs at {@link EventPriority#LOWEST} so that if another installed mod also listens to
 * this event (e.g. its own XP-rate mod, or a perk that grants bonus XP), Modus scales
 * whatever amount that mod already settled on rather than the two mods racing to go first -
 * listener order between mods at the same priority isn't guaranteed, so without this a
 * multiplier mod's effect could unpredictably depend on mod load order.
 * <p>
 * {@code fractionalRemainder} carries each player's rounding leftover forward instead of
 * flooring the scaled amount fresh every event. At the low end of {@link XpConfig#MULTIPLIER}'s
 * [0.1, 10.0] range, a single gain (especially a 1-point one) can legitimately floor down to
 * zero added XP for that one event - that's correct, not a bug: at 0.1x, a lone 1-point gain is
 * only "worth" 0.1 of a point on its own. Without carrying the remainder forward, though, EVERY
 * single-point gain at 0.1x would floor to zero forever, granting no XP at all from a source
 * that only ever grants XP one point at a time, rather than the intended long-run average of a
 * tenth of normal. Adding the previous leftover before flooring (and keeping whatever's still
 * left over for next time) makes the long-run average exactly match the configured multiplier
 * regardless of how small or frequent individual gains are - the standard technique for scaling
 * a stream of discrete quantities by a fraction without a systematic bias in either direction.
 */
@EventBusSubscriber(modid = Modus.MODID)
public final class XpGainHandler {
    /**
     * Per-player leftover fraction not yet reflected in a whole XP point, keyed by player
     * UUID (a global remainder would incorrectly let one player's gains affect another's
     * rounding). Cleared on logout by {@link #onPlayerLoggedOut} so a server seeing many
     * transient players over time doesn't accumulate an ever-growing map of stale entries.
     */
    private static final Map<UUID, Double> fractionalRemainder = new ConcurrentHashMap<>();

    private XpGainHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (!XpConfig.ENABLED.get()) {
            return;
        }

        int amount = event.getAmount();
        if (amount <= 0) {
            // Only scale gains; leave XP losses (commands, other mods, etc.) untouched.
            return;
        }

        if (event.getEntity().level().isClientSide()) {
            // Server is authoritative for XP; avoid touching any client-side prediction path.
            return;
        }

        double multiplier = XpConfig.MULTIPLIER.get();
        if (multiplier == 1.0) {
            return;
        }

        UUID playerId = event.getEntity().getUUID();
        double exact = amount * multiplier + fractionalRemainder.getOrDefault(playerId, 0.0);
        long scaled = (long) Math.floor(exact);
        fractionalRemainder.put(playerId, exact - scaled);

        scaled = Math.max(0, Math.min(scaled, Integer.MAX_VALUE));
        event.setAmount((int) scaled);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        fractionalRemainder.remove(event.getEntity().getUUID());
    }
}
