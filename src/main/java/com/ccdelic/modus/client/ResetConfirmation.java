package com.ccdelic.modus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared "Are you sure you'd like to Reset all values in X?" Yes/No gate for every Reset button
 * across Modus's config screens - both the ones NeoForge's own {@code ConfigurationSectionScreen}
 * / {@code ConfigurationListScreen} generate automatically (see {@link ModusSectionScreen} and
 * {@link BlacklistListScreen}, which reset immediately with zero confirmation out of the box)
 * and the two fully custom ones ({@link ModdedOreScreen}, {@link StructureRarityScreen}) that
 * build their own Reset button from scratch since they aren't backed by a real
 * {@code ModConfigSpec} at all.
 * <p>
 * {@code currentScreen} must already be the screen the Reset button lives on (its own
 * {@code this}) - {@code onConfirmed} runs first on "Yes" (before navigating anywhere), so a
 * caller that resets state via an in-place rebuild (e.g. calling {@code rebuildWidgets()} on
 * itself) sees that rebuilt state immediately once this returns to it; "No" (or Escape, which
 * {@link ConfirmScreen} already treats as "No") skips {@code onConfirmed} entirely and simply
 * returns to {@code currentScreen} untouched.
 */
final class ResetConfirmation {
    private ResetConfirmation() {
    }

    static void show(Screen currentScreen, Component categoryName, Runnable onConfirmed) {
        Component title = Component.translatable("modus.configuration.reset.confirm.title");
        Component message = Component.translatable("modus.configuration.reset.confirm.message", categoryName);
        Minecraft.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                onConfirmed.run();
            }
            Minecraft.getInstance().setScreen(currentScreen);
        }, title, message));
    }
}
