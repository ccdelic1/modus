package com.ccdelic.modus;

import com.ccdelic.modus.client.ModusRootScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Registers {@link ModusRootScreen} so every {@code ModConfigSpec}-backed config file (ore
 * options, mob options, XP, the structure block blacklist) - plus the two dynamically-discovered
 * pages that aren't backed by a real spec at all (Structure Rarity Options, Modded Ore Options) -
 * can be edited from Mods screen &gt; Modus &gt; Config, in addition to hand-editing the TOML
 * files under config/Modus/ directly.
 * <p>
 * {@link ModusRootScreen} replaces NeoForge's stock {@code ConfigurationScreen} as the root
 * screen entirely, rather than customizing it via its section-screen factory the way this class
 * used to: {@code ConfigurationScreen} is {@code final} and unconditionally lists every
 * registered {@code ModConfig} as its own flat top-level button, which can't group the eleven
 * per-ore vanilla settings pages behind one "Vanilla Ore Options..." submenu - see that class's
 * javadoc for the full picture, including why the marker-spec workaround this class previously
 * used for the two dynamically-discovered pages is no longer needed now that the button list is
 * built by hand instead of generically from every registered {@code ModConfig}.
 */
@Mod(value = Modus.MODID, dist = Dist.CLIENT)
public class ModusClient {
    public ModusClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new ModusRootScreen(parent));
    }
}
