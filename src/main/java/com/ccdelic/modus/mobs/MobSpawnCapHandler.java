package com.ccdelic.modus.mobs;

import com.ccdelic.modus.Modus;
import com.ccdelic.modus.config.MobConfig;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Overrides each {@link MobCategory}'s per-chunk spawn cap from {@link MobConfig} once per
 * server start, mirroring referenceMod's {@code HandleServerAboutToStart} (a Forge mod)
 * ported to NeoForge. {@link MobCategory#getMaxInstancesPerChunk()} has no vanilla setter,
 * so this reflectively overwrites the enum constant's backing field - safe here because
 * the field is a non-static instance field (not a compile-time-inlined constant) and, on
 * NeoForge's classpath-based mod loading, not subject to JPMS module access restrictions.
 * <p>
 * Because those enum constants live for the whole JVM session - not per world - a mutated
 * cap would otherwise silently survive leaving a singleplayer world and opening another one
 * with the feature turned off (or turned down): the "disabled means vanilla" contract only
 * holds if something actively puts vanilla's values back. So the true vanilla caps are
 * captured once, before the first mutation ever happens, and every server start begins by
 * restoring them - making each start's outcome depend only on the current config, never on
 * what a previous world in the same session did.
 * <p>
 * Unlike the reference mod (which needed {@code ASMAPI.mapField} to find the obfuscated
 * runtime name), NeoForge 1.21.1 ships official Mojang mappings at runtime too, so the
 * field is simply named "max".
 */
@EventBusSubscriber(modid = Modus.MODID)
public final class MobSpawnCapHandler {
    private static final int MIN_VALID_CAP = 1;
    private static final int MAX_VALID_CAP = 350;

    /**
     * The caps as they were before this mod ever touched them, captured on the first server
     * start of the session (at which point nothing has been mutated yet). Includes any
     * category another mod added via NeoForge's enum extension - restoring those to their
     * own registration-time value is deliberate, since this mod never overrides them and
     * shouldn't leave a stale value there either way.
     */
    private static Map<MobCategory, Integer> vanillaCaps = null;

    private MobSpawnCapHandler() {
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Field maxField;
        try {
            maxField = MobCategory.class.getDeclaredField("max");
            maxField.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            Modus.LOGGER.error("Modus: could not access MobCategory#max via reflection; mob spawn caps will not be overridden.", e);
            return;
        }

        if (vanillaCaps == null) {
            Map<MobCategory, Integer> captured = new EnumMap<>(MobCategory.class);
            for (MobCategory category : MobCategory.values()) {
                captured.put(category, category.getMaxInstancesPerChunk());
            }
            vanillaCaps = captured;
        } else {
            // A previous world in this same game session may have overridden caps; start
            // from a clean vanilla baseline so this world reflects only the current config.
            for (MobCategory category : MobCategory.values()) {
                Integer vanilla = vanillaCaps.get(category);
                if (vanilla != null && vanilla != category.getMaxInstancesPerChunk()) {
                    try {
                        maxField.setInt(category, vanilla);
                    } catch (ReflectiveOperationException e) {
                        Modus.LOGGER.error("Modus: failed to restore vanilla spawn cap for {}.", category.getSerializedName(), e);
                    }
                }
            }
        }

        if (!MobConfig.ENABLED.get()) {
            Modus.LOGGER.info("Modus: mob spawn cap overrides are disabled in config; leaving vanilla spawn caps untouched.");
            return;
        }

        for (MobCategory category : MobCategory.values()) {
            if (category == MobCategory.MISC) {
                // MISC uses -1 as a sentinel for "no per-chunk cap" and must not be touched.
                continue;
            }

            int configured = MobConfig.maxFor(category);
            if (configured == MobConfig.UNSUPPORTED_CATEGORY) {
                // Not one of ours - most likely a category another installed mod registered
                // via MobCategory's NeoForge enum extension. Nothing to do, nothing to warn.
                continue;
            }
            if (configured < MIN_VALID_CAP || configured > MAX_VALID_CAP) {
                Modus.LOGGER.warn(
                    "Modus: configured spawn cap for {} ({}) is outside the valid {}-{} range; ignoring.",
                    category.getSerializedName(), configured, MIN_VALID_CAP, MAX_VALID_CAP
                );
                continue;
            }

            int vanillaMax = category.getMaxInstancesPerChunk();
            if (configured == vanillaMax) {
                continue;
            }

            try {
                maxField.setInt(category, configured);
                Modus.LOGGER.info(
                    "Modus: {} spawn cap changed from {} to {}.", category.getSerializedName(), vanillaMax, configured
                );
            } catch (ReflectiveOperationException e) {
                Modus.LOGGER.error("Modus: failed to override spawn cap for {}.", category.getSerializedName(), e);
            }
        }
    }
}
