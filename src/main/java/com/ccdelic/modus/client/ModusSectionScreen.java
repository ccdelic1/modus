package com.ccdelic.modus.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * Base class for every one of Modus's {@code ModConfigSpec}-backed section screens - every
 * per-ore vanilla settings page, Mob Spawn Caps, XP Settings, and (via
 * {@link BlacklistSectionScreen}) the Structure Block Blacklist's top-level section - used in
 * place of NeoForge's stock {@code ConfigurationSectionScreen} directly. NeoForge's own Reset
 * button applies instantly the moment it's pressed, with no confirmation at all; the only change
 * here is wrapping that exact same button (built completely unmodified via
 * {@code super.createResetButton()}, so its actual reset logic - and the undo-history entry it
 * records - is untouched) behind a {@link ResetConfirmation} Yes/No gate first.
 */
public class ModusSectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {
    public ModusSectionScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title);
    }

    @Override
    protected void createResetButton() {
        super.createResetButton();
        Button original = resetButton;
        resetButton = Button.builder(ConfigurationScreen.RESET, button -> ResetConfirmation.show(this, getTitle(), original::onPress))
            .tooltip(Tooltip.create(ConfigurationScreen.RESET_TOOLTIP))
            .width(Button.SMALL_WIDTH)
            .build();
    }
}
