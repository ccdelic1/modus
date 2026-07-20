package com.ccdelic.modus.xp;

import com.ccdelic.modus.Modus;
import com.ccdelic.modus.config.XpConfig;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
 */
@EventBusSubscriber(modid = Modus.MODID)
public final class XpGainHandler {
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

        long scaled = Math.round(amount * multiplier);
        scaled = Math.max(0, Math.min(scaled, Integer.MAX_VALUE));
        event.setAmount((int) scaled);
    }
}
