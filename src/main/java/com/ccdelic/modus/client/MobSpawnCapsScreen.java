package com.ccdelic.modus.client;

import com.ccdelic.modus.config.MobConfig;
import com.ccdelic.modus.mobs.ModdedMobCategoryRegistry;
import com.ccdelic.modus.mobs.MobSpawnCapHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The "Mob Spawn Caps" config screen: the seven vanilla category fields ({@link MobConfig}) plus
 * one row for every modded spawn category {@link ModdedMobCategoryRegistry} detects. Built as a
 * fully custom screen, like {@code ModdedOreScreen} / {@code StructureRarityScreen}, rather than
 * NeoForge's generic {@code ConfigurationSectionScreen} this page used before: that generic
 * system CAN append extra rows via {@code createSyntheticValues()}, but only ever at the bottom
 * of the list, with no supported way to put a warning banner literally at the top the way this
 * (and the other two dynamically-discovered-content screens) need - and unlike those two, the
 * "top" here also has to sit above seven already-existing vanilla fields, not an otherwise-empty
 * page.
 * <p>
 * The seven vanilla fields bind straight to {@link MobConfig}'s {@code ModConfigSpec} values -
 * {@code IntValue#set()} only updates the in-memory value (no file I/O), so unlike the modded
 * rows below (backed by {@link ModdedMobCategoryRegistry}'s raw TOML file, where a write is a
 * real, comparatively expensive file open/sync/close) these commit on every valid keystroke, the
 * same as NeoForge's own generic int-value editor does; the actual TOML file write happens once,
 * deferred to this screen closing ({@link #removed()} calls {@link MobConfig#SPEC}{@code .save()}),
 * mirroring exactly when NeoForge's own generic screen would have written it.
 * <p>
 * Modded rows share {@link MobConfig#ENABLED} as their own master switch rather than getting a
 * second one - see {@link ModdedMobCategoryRegistry}'s class javadoc.
 */
public class MobSpawnCapsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("modus.configuration.mobSpawnCaps.title");
    private static final Component WARNING = Component.translatable("modus.configuration.mobSpawnCaps.warning");
    private static final Component MODDED_HEADER = Component.translatable("modus.configuration.mobSpawnCaps.moddedHeader");
    private static final Component ENABLED_LABEL = Component.translatable("modus.configuration.enabled");
    private static final Component ENABLED_TOOLTIP = Component.translatable("modus.configuration.mobSpawnCaps.enabled.tooltip");
    private static final Component MODDED_TOOLTIP = Component.translatable("modus.configuration.moddedMobCategory.tooltip");
    private static final int ROW_WIDTH = 310;

    private final List<Runnable> pendingCommits = new ArrayList<>();
    private boolean vanillaFieldsChanged = false;

    public MobSpawnCapsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, TITLE);
    }

    /**
     * Flushes any modded-row edit still sitting uncommitted in a focused field, then writes the
     * seven vanilla fields to disk if any changed - see the class javadoc for why those are
     * deferred to here instead of committed per-field like the modded rows are.
     */
    @Override
    public void removed() {
        for (Runnable commit : pendingCommits) {
            commit.run();
        }
        if (vanillaFieldsChanged) {
            MobConfig.SPEC.save();
            vanillaFieldsChanged = false;
        }
        super.removed();
    }

    /**
     * This screen isn't reached through {@link ModusSectionScreen} anymore (see the class
     * javadoc), so it doesn't inherit that class's confirmation-wrapped Reset button either -
     * needs its own, built the same way {@code OptionsSubScreen}'s default {@code addFooter()}
     * builds the "Done" button, just with a "Reset" button next to it.
     */
    @Override
    protected void addFooter() {
        LinearLayout linearlayout = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearlayout.addChild(
            Button.builder(ConfigurationScreen.RESET, button -> ResetConfirmation.show(this, getTitle(), this::resetAll))
                .tooltip(Tooltip.create(ConfigurationScreen.RESET_TOOLTIP))
                .width(Button.SMALL_WIDTH)
                .build()
        );
        linearlayout.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(Button.SMALL_WIDTH).build());
    }

    /**
     * Resets every vanilla category to its true vanilla default ({@link MobSpawnCapHandler#vanillaMaxFor})
     * and every detected modded category to its own captured default, then repopulates
     * {@link #list} in place - see {@code ModdedOreScreen#resetAll} for why this clears and
     * re-runs {@link #addOptions()} directly rather than a full {@code rebuildWidgets()}.
     */
    private void resetAll() {
        MobConfig.ENABLED.set(false);
        MobConfig.MONSTER_MAX.set(MobSpawnCapHandler.vanillaMaxFor(MobCategory.MONSTER));
        MobConfig.CREATURE_MAX.set(MobSpawnCapHandler.vanillaMaxFor(MobCategory.CREATURE));
        MobConfig.AMBIENT_MAX.set(MobSpawnCapHandler.vanillaMaxFor(MobCategory.AMBIENT));
        MobConfig.AXOLOTLS_MAX.set(MobSpawnCapHandler.vanillaMaxFor(MobCategory.AXOLOTLS));
        MobConfig.UNDERGROUND_WATER_CREATURE_MAX.set(MobSpawnCapHandler.vanillaMaxFor(MobCategory.UNDERGROUND_WATER_CREATURE));
        MobConfig.WATER_CREATURE_MAX.set(MobSpawnCapHandler.vanillaMaxFor(MobCategory.WATER_CREATURE));
        MobConfig.WATER_AMBIENT_MAX.set(MobSpawnCapHandler.vanillaMaxFor(MobCategory.WATER_AMBIENT));
        MobConfig.SPEC.save();
        vanillaFieldsChanged = false;

        for (MobCategory category : ModdedMobCategoryRegistry.allEntries().keySet()) {
            ModdedMobCategoryRegistry.save(category, new ModdedMobCategoryRegistry.Entry(MobSpawnCapHandler.vanillaMaxFor(category)));
        }

        pendingCommits.clear();
        list.children().clear();
        addOptions();
    }

    @Override
    protected void addOptions() {
        // See ModdedMobCategoryRegistry's class javadoc for why this is safe and cheap to call
        // every time this screen builds its rows: it only actually scans once, ever, per
        // session, and every later call (including this one, on a fresh install where the
        // very first server start hasn't happened yet) is a no-op if it already ran.
        ModdedMobCategoryRegistry.ensureDiscovered();

        addWarningRows();

        Checkbox enabledCheckbox = Checkbox.builder(Component.empty(), font)
            .selected(MobConfig.ENABLED.get())
            .tooltip(Tooltip.create(ENABLED_TOOLTIP))
            .onValueChange((checkbox, value) -> {
                MobConfig.ENABLED.set(value);
                vanillaFieldsChanged = true;
            })
            .build();
        list.addSmall(new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, ENABLED_LABEL, font).alignLeft(), enabledCheckbox);

        addVanillaRow("modus.configuration.monsterMax", MobConfig.MONSTER_MAX);
        addVanillaRow("modus.configuration.creatureMax", MobConfig.CREATURE_MAX);
        addVanillaRow("modus.configuration.ambientMax", MobConfig.AMBIENT_MAX);
        addVanillaRow("modus.configuration.axolotlsMax", MobConfig.AXOLOTLS_MAX);
        addVanillaRow("modus.configuration.undergroundWaterCreatureMax", MobConfig.UNDERGROUND_WATER_CREATURE_MAX);
        addVanillaRow("modus.configuration.waterCreatureMax", MobConfig.WATER_CREATURE_MAX);
        addVanillaRow("modus.configuration.waterAmbientMax", MobConfig.WATER_AMBIENT_MAX);

        Map<MobCategory, ModdedMobCategoryRegistry.Entry> modded = ModdedMobCategoryRegistry.allEntries();
        if (modded.isEmpty()) {
            return;
        }

        list.addSmall(new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, Component.empty(), font), null);
        list.addSmall(new StringWidget(ROW_WIDTH, Button.DEFAULT_HEIGHT, MODDED_HEADER, font).alignLeft(), null);

        modded.keySet().stream()
            .sorted(Comparator.comparing(MobCategory::getSerializedName))
            .forEach(category -> addModdedRow(category, modded.get(category)));
    }

    /**
     * Auto-discovery can't tell a genuine modded spawn category apart from anything else
     * registered into {@link MobCategory} - a heuristic limitation worth surfacing to the
     * player before they start capping entries. Pre-wrapped into several rows rather than one
     * auto-wrapping widget, each still rendered through {@link ScalingLabelWidget} - see
     * {@code ModdedOreScreen#addWarningRows} for why both of those matter.
     */
    private void addWarningRows() {
        for (String line : ClientTextUtil.wrapForRows(font, WARNING.getString(), ROW_WIDTH)) {
            Component styled = Component.literal(line).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            ScalingLabelWidget lineWidget = new ScalingLabelWidget(0, 0, ROW_WIDTH, Button.DEFAULT_HEIGHT, styled, font, ROW_WIDTH, true);
            list.addSmall(lineWidget, null);
        }
    }

    private void addVanillaRow(String translationKey, ModConfigSpec.IntValue configValue) {
        Component label = Component.translatable(translationKey);
        Component tooltip = Component.translatable(translationKey + ".tooltip");

        EditBox box = new EditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, label);
        box.setMaxLength(4);
        box.setValue(Integer.toString(configValue.get()));
        box.setTooltip(Tooltip.create(tooltip));
        box.setResponder(text -> {
            // Matches the [1, 350] range MobConfig itself defines every vanilla IntValue with.
            Integer parsed = tryParseInt(text, 1, 350);
            box.setTextColor(parsed != null ? EditBox.DEFAULT_TEXT_COLOR : 0xFFFF0000);
            if (parsed != null) {
                // Cheap in-memory write - see the class javadoc for why this commits every
                // keystroke instead of waiting for unfocus, unlike the modded rows below.
                configValue.set(parsed);
                vanillaFieldsChanged = true;
            }
        });

        list.addSmall(new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, label, font).alignLeft(), box);
    }

    private void addModdedRow(MobCategory category, ModdedMobCategoryRegistry.Entry entry) {
        Component label = Component.literal(ClientTextUtil.humanizeSegment(category.getSerializedName()));

        CommitOnUnfocusEditBox box = new CommitOnUnfocusEditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, label);
        box.setMaxLength(4);
        box.setValue(Integer.toString(entry.max()));
        box.setTooltip(Tooltip.create(MODDED_TOOLTIP));
        box.setResponder(text -> {
            Integer parsed = tryParseInt(text, ModdedMobCategoryRegistry.MIN_CAP, ModdedMobCategoryRegistry.MAX_CAP);
            box.setTextColor(parsed != null ? EditBox.DEFAULT_TEXT_COLOR : 0xFFFF0000);
            if (parsed != null) {
                // In-memory only - no disk write per keystroke, matching ModdedOreRegistry's
                // own reasoning for why that split matters for a raw-TOML-backed value.
                ModdedMobCategoryRegistry.applyInMemory(category, new ModdedMobCategoryRegistry.Entry(parsed));
            }
        });

        Runnable commit = () -> {
            Integer parsed = tryParseInt(box.getValue(), ModdedMobCategoryRegistry.MIN_CAP, ModdedMobCategoryRegistry.MAX_CAP);
            if (parsed != null) {
                ModdedMobCategoryRegistry.save(category, new ModdedMobCategoryRegistry.Entry(parsed));
            }
        };
        box.setOnUnfocus(commit);
        pendingCommits.add(commit);

        list.addSmall(new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, label, font).alignLeft(), box);
    }

    private static Integer tryParseInt(String text, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (value < min || value > max) {
            return null;
        }
        return value;
    }

    /**
     * An {@link EditBox} that runs a callback when it loses focus - the commit point the
     * modded-category rows write to disk at, instead of on every keystroke (see the class
     * javadoc).
     */
    private static final class CommitOnUnfocusEditBox extends EditBox {
        private Runnable onUnfocus = () -> {};

        CommitOnUnfocusEditBox(Font font, int width, int height, Component message) {
            super(font, width, height, message);
        }

        void setOnUnfocus(Runnable onUnfocus) {
            this.onUnfocus = onUnfocus;
        }

        @Override
        public void setFocused(boolean focused) {
            boolean wasFocused = this.isFocused();
            super.setFocused(focused);
            if (wasFocused && !focused) {
                this.onUnfocus.run();
            }
        }
    }
}
