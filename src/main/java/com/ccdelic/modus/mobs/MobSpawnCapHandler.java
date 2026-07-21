package com.ccdelic.modus.mobs;

import com.ccdelic.modus.Modus;
import com.ccdelic.modus.config.MobConfig;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
 * <p>
 * If the reflective access fails for any reason - the field being renamed/removed in a
 * future Minecraft version is the realistic case; strict JPMS module access denial is not
 * actually reachable on NeoForge's classpath-based mod loading, per the javadoc above, but
 * the same catch block covers both regardless - the only trace today would be a server-log
 * ERROR line, which a typical player (and most server admins, most of the time) never looks
 * at. An enabled feature would then silently do nothing with zero in-game indication anything
 * was wrong. {@link #onPlayerLoggedIn} closes that gap by telling operators directly.
 * <p>
 * {@code MobCategory#max} itself is a plain (non-{@code volatile}) vanilla field this class
 * can't redeclare, so the write via reflection here has no explicit memory-visibility
 * guarantee toward whatever later reads {@link MobCategory#getMaxInstancesPerChunk()} - in
 * isolation, a textbook cross-thread data race. In practice this doesn't bite: {@code
 * ServerAboutToStartEvent} fires on the main server thread before that same thread ever
 * enters its tick loop, and natural mob spawning (the only normal reader of this field) also
 * runs on that same main server thread during ticking - so the write and its reads share a
 * thread, with no cross-thread visibility gap to close. There is no way to add {@code
 * volatile} to a field this class doesn't own, and no way to make vanilla's own read call
 * sites synchronize on anything Modus controls, so this is documented rather than "fixed" -
 * an ineffective code change here would add complexity without closing any actual gap.
 */
@EventBusSubscriber(modid = Modus.MODID)
public final class MobSpawnCapHandler {
    private static final int MIN_VALID_CAP = 1;
    private static final int MAX_VALID_CAP = 350;

    /** The permission level (matches vanilla's "most admin commands" tier) required to receive {@link #onPlayerLoggedIn}'s notice. */
    private static final int OPERATOR_PERMISSION_LEVEL = 2;

    /**
     * The caps as they were before this mod ever touched them. Captured in a static
     * initializer rather than lazily on the first {@code ServerAboutToStartEvent} - this
     * class is loaded (running its static initializer) when NeoForge's automatic event-bus
     * subscriber scan discovers its {@code @SubscribeEvent} methods during mod loading, well
     * before any {@code ServerAboutToStartEvent} fires for this or any other mod. Capturing
     * this early - rather than relying on Modus's own handler simply running before any
     * other mod's - is what guarantees these are the true vanilla values: handler order
     * between mods for the same event depends on {@code EventPriority} (and is unordered
     * between handlers at the same priority), so "run first" was only ever a probabilistic
     * mitigation, never a guarantee. Includes any category another mod added via NeoForge's
     * enum extension - restoring those to their own registration-time value is deliberate,
     * since this mod never overrides them and shouldn't leave a stale value there either way.
     */
    private static final Map<MobCategory, Integer> vanillaCaps;

    static {
        Map<MobCategory, Integer> captured = new EnumMap<>(MobCategory.class);
        for (MobCategory category : MobCategory.values()) {
            captured.put(category, category.getMaxInstancesPerChunk());
        }
        vanillaCaps = captured;
    }

    /**
     * Whether any part of this server start's reflective override/restore work failed - reset
     * at the top of every {@link #onServerAboutToStart}, so a failure can never linger and
     * misreport a later, successful start within the same session. Read by
     * {@link #onPlayerLoggedIn} to decide whether an operator needs telling.
     */
    private static volatile boolean reflectionFailed = false;

    private MobSpawnCapHandler() {
    }

    /**
     * The true, never-mutated default max instances per chunk for {@code category} - captured
     * once in {@link #vanillaCaps}'s static initializer, before this class (or anything else)
     * ever reflectively touches {@link MobCategory#max}. {@link ModdedMobCategoryRegistry} uses
     * this (rather than reading {@link MobCategory#getMaxInstancesPerChunk()} live) as the seed
     * default for a modded category's config file, for the identical reason {@code vanillaCaps}
     * itself exists: reading the field live could observe an already-overridden value if this
     * is called after a world with overrides enabled has been played this session but before
     * the next server start restores it. The {@code getOrDefault} fallback only matters for a
     * category some other mod registers into {@link MobCategory} after this class's static
     * initializer already ran (a real possibility for {@code IExtensibleEnum}, if unusual) -
     * {@link MobCategory#getMaxInstancesPerChunk()} is still the best available answer for one
     * that's never been mutated by this handler at all.
     */
    public static int vanillaMaxFor(MobCategory category) {
        return vanillaCaps.getOrDefault(category, category.getMaxInstancesPerChunk());
    }

    /**
     * A server crash report was once observed with "Failed to initialize server" and no root
     * exception preserved anywhere in the report, meaning one of this mod's three
     * {@code ServerAboutToStartEvent} handlers may have thrown - but which one, and why,
     * couldn't be determined after the fact. This catches, logs, and rethrows anything not
     * already handled by the fine-grained reflective-operation catches below, so if this ever
     * happens again, Modus's own log shows a clear, attributable stack trace for THIS handler
     * specifically, ahead of whatever swallows the exception further up the chain. Rethrowing
     * (rather than swallowing) means server startup still fails exactly as it would have
     * without this try/catch - this isn't meant to let startup limp along with mob spawn caps
     * half-applied.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            doOnServerAboutToStart();
        } catch (RuntimeException e) {
            Modus.LOGGER.error("Modus: mob spawn cap setup failed during server startup. Rethrowing so server startup still fails as it would have without this handler.", e);
            throw e;
        }
    }

    private static void doOnServerAboutToStart() {
        reflectionFailed = false;
        // Harmless if this has already run (e.g. the config screen was opened before the first
        // server start this session) - see ModdedMobCategoryRegistry's class javadoc for why a
        // one-time discovery is correct for mob categories, unlike ore/structure registries.
        ModdedMobCategoryRegistry.ensureDiscovered();

        Field maxField;
        try {
            maxField = MobCategory.class.getDeclaredField("max");
            maxField.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            Modus.LOGGER.error("Modus: could not access MobCategory#max via reflection; mob spawn caps will not be overridden.", e);
            reflectionFailed = true;
            return;
        }

        // A previous world in this same game session, or another mod's own
        // ServerAboutToStartEvent handler running before this one, may have changed these
        // fields since vanillaCaps was captured; start from that clean baseline so this
        // world's outcome reflects only the current config.
        for (MobCategory category : MobCategory.values()) {
            Integer vanilla = vanillaCaps.get(category);
            if (vanilla != null && vanilla != category.getMaxInstancesPerChunk()) {
                try {
                    maxField.setInt(category, vanilla);
                } catch (ReflectiveOperationException e) {
                    Modus.LOGGER.error("Modus: failed to restore vanilla spawn cap for {}.", category.getSerializedName(), e);
                    reflectionFailed = true;
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
                // Not one of the seven vanilla categories MobConfig itself covers - check
                // whether ModdedMobCategoryRegistry recognized it as a modded spawn group
                // instead (it shares this same ENABLED master switch, so no separate check
                // needed here for that).
                ModdedMobCategoryRegistry.Entry moddedEntry = ModdedMobCategoryRegistry.get(category);
                if (moddedEntry == null) {
                    // Genuinely nothing Modus knows about at all - logged at DEBUG, same as
                    // ModdedOreRegistry does for an unrecognized placed feature, so a curious
                    // user can turn on debug logging and see why a given category isn't
                    // configurable through Modus without a code change. Shouldn't normally
                    // happen, since ModdedMobCategoryRegistry is supposed to have already
                    // classified every non-vanilla category by this point.
                    Modus.LOGGER.debug("Modus: {} is not a category Modus manages; leaving its spawn cap untouched.", category.getSerializedName());
                    continue;
                }
                configured = moddedEntry.max();
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
                reflectionFailed = true;
            }
        }
    }

    /**
     * Notifies operators, on login, if mob spawn cap overrides are enabled in config but
     * failed to apply this server start - see the class javadoc for why a server-log ERROR
     * line alone isn't enough. Gated on both {@link #reflectionFailed} and
     * {@link MobConfig#ENABLED}, so this can never fire for the overwhelming common case
     * (reflection succeeds, or the feature isn't even turned on), and on the joining player's
     * permission level, so it only reaches the people who could actually act on it.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!reflectionFailed || !MobConfig.ENABLED.get()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(OPERATOR_PERMISSION_LEVEL)) {
            serverPlayer.sendSystemMessage(Component.translatable("modus.notification.mobSpawnCapReflectionFailed"));
        }
    }
}
