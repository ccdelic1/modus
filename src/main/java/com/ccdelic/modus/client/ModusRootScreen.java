package com.ccdelic.modus.client;

import com.ccdelic.modus.config.XpConfig;
import com.ccdelic.modus.structures.StructureBlacklistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Modus's own root config screen, reached from Mods screen &gt; Modus &gt; Config in place of
 * NeoForge's stock {@code ConfigurationScreen}. {@code ConfigurationScreen} is {@code final}
 * and unconditionally lists every {@code ModConfig} registered for this mod as its own flat
 * top-level button, with no supported hook to group several of them behind one submenu -
 * exactly what's needed to collapse the eleven per-ore vanilla settings pages ("Coal Ore
 * Settings...", "Iron Ore Settings...", etc.) behind a single "Vanilla Ore Options..." button
 * (see {@link VanillaOreOptionsScreen}) instead of listing all eleven directly here alongside
 * everything else.
 * <p>
 * Every other page opens the exact same kind of screen NeoForge's generic system would have
 * built for it - {@link ModusSectionScreen} for the one remaining plain {@code ModConfigSpec}
 * page (XP Settings), {@link BlacklistSectionScreen} for the structure block blacklist, and
 * {@link StructureRarityScreen} / {@link ModdedOreScreen} / {@link MobSpawnCapsScreen} for the
 * fully custom pages - the first two because they aren't backed by a real {@code ModConfigSpec}
 * at all (discovered dynamically after data packs load), the third because it needs a warning
 * banner literally above its {@code ModConfigSpec} fields, which NeoForge's generic system has
 * no supported way to do (see that class's own javadoc). {@link StructureRarityScreen} and
 * {@link ModdedOreScreen} were previously reached via marker {@code ModConfigSpec}s
 * ({@code StructureRarityMenuMarker} / {@code ModdedOreMenuMarker}) purely so NeoForge's generic
 * list would have a button for them - since this screen builds its button list by hand instead,
 * those markers (and registering them) are no longer needed.
 */
public class ModusRootScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("modus.configuration.title");
    private static final int ROW_WIDTH = 310;

    public ModusRootScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addOptions() {
        addSectionButton(
            "modus.configuration.section.modus.oreoptions.vanillaoremenu.toml",
            button -> minecraft.setScreen(new VanillaOreOptionsScreen(this))
        );
        addSectionButton(
            "modus.configuration.section.modus.moboptions.mobs.toml",
            button -> minecraft.setScreen(new MobSpawnCapsScreen(this))
        );
        addConfigSectionButton(
            "modus.configuration.section.modus.xp.xp.toml",
            XpConfig.SPEC,
            (modConfig, title) -> new ModusSectionScreen(this, ModConfig.Type.COMMON, modConfig, title)
        );
        addConfigSectionButton(
            "modus.configuration.section.modus.structureoptions.blacklist.toml",
            StructureBlacklistConfig.SPEC,
            (modConfig, title) -> new BlacklistSectionScreen(this, ModConfig.Type.COMMON, modConfig, title)
        );
        addSectionButton(
            "modus.configuration.section.modus.structureoptions.raritymenu.toml",
            button -> minecraft.setScreen(new StructureRarityScreen(this))
        );
        addSectionButton(
            "modus.configuration.section.modus.oreoptions.moddedoremenu.toml",
            button -> minecraft.setScreen(new ModdedOreScreen(this))
        );
    }

    /**
     * A button whose target screen titles itself (both {@link StructureRarityScreen} and
     * {@link ModdedOreScreen} carry their own fixed title internally), so only the button's own
     * label - {@code baseKey}, wrapped in NeoForge's usual {@code "%s..."} section-button
     * format - is needed here.
     */
    private void addSectionButton(String baseKey, Button.OnPress onPress) {
        Component label = Component.translatable("neoforge.configuration.uitext.section", Component.translatable(baseKey));
        list.addSmall(Button.builder(label, onPress).width(ROW_WIDTH).build(), null);
    }

    /**
     * A button leading to a real {@code ModConfigSpec}-backed section screen, which - unlike
     * {@link #addSectionButton} - needs an explicit title Component passed to its constructor
     * (mirroring NeoForge's own generic {@code ConfigurationSectionScreen} signature). Skips
     * adding the button entirely (rather than crashing every other button on this screen) if
     * {@link ModConfigLookup} can't find the backing {@code ModConfig} - see its own javadoc
     * for why that's a "should never happen, but harden it anyway" case.
     */
    private void addConfigSectionButton(String baseKey, ModConfigSpec spec, ScreenFactory factory) {
        ModConfig modConfig = ModConfigLookup.find(spec);
        if (modConfig == null) {
            return;
        }
        Component label = Component.translatable("neoforge.configuration.uitext.section", Component.translatable(baseKey));
        Component title = Component.translatable(baseKey + ".title");
        list.addSmall(Button.builder(label, button -> minecraft.setScreen(factory.create(modConfig, title))).width(ROW_WIDTH).build(), null);
    }

    @FunctionalInterface
    private interface ScreenFactory {
        Screen create(ModConfig modConfig, Component title);
    }
}
