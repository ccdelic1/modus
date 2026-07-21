package com.ccdelic.modus.client;

import com.ccdelic.modus.Modus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Finds the live {@link ModConfig} NeoForge created for a given {@link ModConfigSpec} after
 * {@code modContainer.registerConfig(...)} registered it in {@code Modus}'s constructor -
 * needed because {@link ModusSectionScreen} (like NeoForge's own {@code
 * ConfigurationSectionScreen} it's based on) is constructed from the real {@link ModConfig}
 * object, not the spec alone, but {@link ModusRootScreen} and {@link VanillaOreOptionsScreen}
 * only ever have direct references to the specs (e.g. {@code MobConfig.SPEC},
 * {@code OreConfig.spec(ore)}) - the same lookup NeoForge's own generic
 * {@code ConfigurationScreen} does internally via {@code ModConfigs.getConfigSet(type)}, just
 * keyed by spec identity instead of iterated wholesale.
 * <p>
 * Returns {@code null} rather than throwing if nothing matches: both callers build several
 * unrelated buttons in a single {@code addOptions()} pass (one call per ore for {@link
 * VanillaOreOptionsScreen}), so a lookup failure for one spec must not be allowed to take down
 * every other button on the page along with it - in practice this should never happen (every
 * spec this is ever called with is registered synchronously in {@code Modus}'s constructor,
 * long before any screen can open), but the whole point of hardening a "should never happen"
 * path is to fail one row instead of the whole menu if it ever does.
 */
final class ModConfigLookup {
    private ModConfigLookup() {
    }

    static ModConfig find(ModConfigSpec spec) {
        for (ModConfig modConfig : ModConfigs.getConfigSet(ModConfig.Type.COMMON)) {
            if (modConfig.getSpec() == spec) {
                return modConfig;
            }
        }
        Modus.LOGGER.error(
            "Modus: no registered ModConfig found for spec {} - was it registered via modContainer.registerConfig(...)? "
                + "The corresponding config screen button will be skipped.",
            spec
        );
        return null;
    }
}
