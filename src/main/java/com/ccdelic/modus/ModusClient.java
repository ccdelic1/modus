package com.ccdelic.modus;

import com.ccdelic.modus.client.BlacklistSectionScreen;
import com.ccdelic.modus.client.ModdedOreScreen;
import com.ccdelic.modus.client.StructureRarityScreen;
import com.ccdelic.modus.oregen.ModdedOreMenuMarker;
import com.ccdelic.modus.structures.StructureBlacklistConfig;
import com.ccdelic.modus.structures.StructureRarityMenuMarker;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Registers NeoForge's built-in config screen so every {@code ModConfigSpec}-backed config
 * file (ore options, mob options, XP, the structure block blacklist) can be edited from Mods
 * screen > Modus > Config, in addition to hand-editing the TOML files under config/Modus/
 * directly.
 * <p>
 * Per-structure rarity options (see {@link com.ccdelic.modus.structures.StructureRarityRegistry})
 * aren't registered through {@code ModConfigSpec} - they're discovered dynamically after
 * world load, too late for {@code ConfigurationScreen} (which builds its button list from
 * every {@code ModConfig} registered via {@code ModContainer#registerConfig}) to know about
 * them normally. {@link StructureRarityMenuMarker} works around that: an intentionally empty
 * spec registered purely so a "Structure Rarity Options" button exists in that same list,
 * alongside "Structure Block Blacklist" and the rest, which {@link #sectionScreenFor}
 * recognizes and routes straight to {@link StructureRarityScreen} instead of the (otherwise
 * empty) default section screen.
 * <p>
 * Modded ores get the same treatment: {@link ModdedOreMenuMarker} adds a "Modded Ore Options"
 * button that routes to {@link ModdedOreScreen}, while vanilla ores keep their own stock
 * per-ore sections. Both dynamic screens are pre-populated before any world loads by
 * {@link com.ccdelic.modus.client.WorldgenConfigBootstrap}.
 * <p>
 * The structure block blacklist's section screen is swapped for {@link BlacklistSectionScreen}
 * (which in turn swaps just its "blacklist" list field for {@link
 * com.ccdelic.modus.client.BlacklistListScreen}) via the same mechanism - the 3-argument
 * {@code ConfigurationScreen} constructor, which NeoForge's own class javadoc documents for
 * exactly this purpose. Every other config keeps the stock, auto-generated section screen
 * unchanged.
 */
@Mod(value = Modus.MODID, dist = Dist.CLIENT)
public class ModusClient {
    public ModusClient(ModContainer container) {
        container.registerExtensionPoint(
            IConfigScreenFactory.class,
            (mc, parent) -> new ConfigurationScreen(container, parent, ModusClient::sectionScreenFor)
        );
    }

    public static Screen sectionScreenFor(
        ConfigurationScreen screen, ModConfig.Type type, ModConfig modConfig, Component title
    ) {
        if (modConfig.getSpec() == StructureBlacklistConfig.SPEC) {
            return new BlacklistSectionScreen(screen, type, modConfig, title);
        }
        if (modConfig.getSpec() == StructureRarityMenuMarker.SPEC) {
            return new StructureRarityScreen(screen);
        }
        if (modConfig.getSpec() == ModdedOreMenuMarker.SPEC) {
            return new ModdedOreScreen(screen);
        }
        return new ConfigurationSectionScreen(screen, type, modConfig, title);
    }
}
