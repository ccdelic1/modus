package com.ccdelic.modus.client;

import com.ccdelic.modus.structures.StructureRarityRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/**
 * Lists every structure {@link StructureRarityRegistry} currently knows about, with an
 * "enabled" checkbox and a rarity field for each - the in-game equivalent of hand-editing
 * the TOML files under {@code config/Modus/structureOptions/vanilla/} and {@code
 * .../modded/<Mod Name>/}.
 * <p>
 * Reached directly as the "Structure Rarity Options" button on {@code ModusRootScreen},
 * constructed straight from there - unlike a value backed by a real {@code ModConfigSpec},
 * there's no config file this screen could be generated from, since the structure list itself
 * is discovered dynamically after data packs load.
 * <p>
 * Normally empty until a world has loaded at least once this session, since the structure
 * list comes from {@code Registries.STRUCTURE}, which doesn't exist until a server
 * (singleplayer or dedicated) has actually started - {@link WorldgenConfigBootstrap} exists
 * specifically to close that gap by resolving a stand-in registry in the background before
 * that happens, so in practice this is usually already populated well before a player
 * navigates here. There's no batch/undo system here the way NeoForge's own config screens
 * have, since this data already hot-reloads independently of any of that.
 * <p>
 * Each rarity field applies its value in memory on every keystroke (so world generation and
 * this screen's own display see it immediately - {@link StructureRarityRegistry#applyInMemory}),
 * but only writes it to disk ({@link StructureRarityRegistry#save}) once the edit is
 * committed: the field losing focus, the enabled checkbox being toggled, or this screen
 * closing (see {@link #removed()}, a safety net in case a field is still focused when the
 * player backs out). {@code CommentedFileConfig}'s {@code sync()} option flushes to disk on
 * every {@code save()} call, so writing on every keystroke instead of every commit would mean
 * several full file-open/write/sync/close cycles for one intended edit.
 */
public class StructureRarityScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("modus.configuration.structureRarity.title");
    private static final Component WARNING = Component.translatable("modus.configuration.structureRarity.warning");
    private static final Component EMPTY_MESSAGE = Component.translatable("modus.configuration.structureRarity.empty");
    private static final Component ENABLED_TOOLTIP = Component.translatable("modus.configuration.structureRarity.enabled.tooltip");
    private static final Component RARITY_LABEL = Component.translatable("modus.configuration.rarity.short");
    private static final Component RARITY_TOOLTIP = Component.translatable("modus.configuration.structureRarity.rarity.tooltip");
    private static final Component RARITY_NO_INCREASE_TOOLTIP = Component.translatable("modus.configuration.structureRarity.rarity.noIncreaseEffect.tooltip");
    /** Matches the game's usual "this is valid but you should know something" warning color (vanilla uses this same shade for e.g. experimental-feature warnings). */
    private static final int NO_EFFECT_TEXT_COLOR = 0xFFFFAA00;
    private static final int ROW_WIDTH = 310;

    private final List<Runnable> pendingCommits = new ArrayList<>();

    public StructureRarityScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, TITLE);
    }

    /**
     * Flushes any edit still sitting uncommitted in a focused field (see the class javadoc) -
     * losing focus normally triggers the same flush, but the field the player was last typing
     * in is never explicitly unfocused when the screen itself closes.
     */
    @Override
    public void removed() {
        for (Runnable commit : pendingCommits) {
            commit.run();
        }
        super.removed();
    }

    /**
     * This screen isn't backed by a real {@code ModConfigSpec}, so it doesn't get NeoForge's
     * auto-generated Reset button {@link ModusSectionScreen} adds a confirmation prompt in
     * front of - it needs its own from scratch, built the same way {@code OptionsSubScreen}'s
     * default {@code addFooter()} builds the "Done" button, just with a "Reset" button added
     * next to it that resets every known structure back to
     * {@link StructureRarityRegistry.Entry#DEFAULT} (enabled, 1.0x rarity) after the player
     * confirms via {@link ResetConfirmation}.
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
     * Resets every currently-known structure to {@link StructureRarityRegistry.Entry#DEFAULT}
     * and repopulates {@link #list} in place so every row immediately reflects the reset
     * values.
     * <p>
     * Deliberately clears and re-runs {@link #addOptions()} directly rather than calling
     * {@code rebuildWidgets()} (which reruns this screen's full {@code init()}): {@code init()}
     * calls {@code addContents()}, which adds a brand new {@code OptionsList} to {@link #layout}
     * - but {@code layout} is a one-time field, set up once when this screen was first opened
     * and never cleared afterward, so a second {@code init()} pass on the same screen instance
     * leaves the OLD list's widgets sitting in {@code layout} right alongside the new ones
     * rather than replacing them. Both then render on top of each other (the visible symptom:
     * a field's old value staying visible under its new one, and clicks landing on the
     * orphaned, no-longer-updated old widget instead of the new one). NeoForge's own
     * {@code ConfigurationSectionScreen#rebuild()} avoids exactly this by only ever clearing
     * and refilling the existing list, never touching {@code layout} again after the first
     * {@code init()} - this mirrors that. {@link #pendingCommits} is cleared for the same
     * reason: it's about to be repopulated from scratch by {@link #addStructureRow}, and the
     * entries from before the reset would otherwise also still fire, against now-discarded
     * widgets, the next time this screen closes.
     */
    private void resetAll() {
        for (ResourceLocation id : StructureRarityRegistry.allEntries().keySet()) {
            StructureRarityRegistry.save(id, StructureRarityRegistry.Entry.DEFAULT);
        }
        pendingCommits.clear();
        list.children().clear();
        addOptions();
    }

    @Override
    protected void addOptions() {
        addWarningRows();

        Map<ResourceLocation, StructureRarityRegistry.Entry> entries = StructureRarityRegistry.allEntries();
        if (entries.isEmpty()) {
            list.addSmall(new StringWidget(ROW_WIDTH, Button.DEFAULT_HEIGHT, EMPTY_MESSAGE, font).alignLeft(), null);
            return;
        }

        entries.keySet().stream()
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .forEach(id -> addStructureRow(id, entries.get(id)));
    }

    /**
     * Auto-discovery just scans {@code Registries.STRUCTURE}, so it can't tell an actual
     * structure apart from anything else registered there under an unusual id - a heuristic
     * limitation, not a bug, but one worth surfacing to the player before they start disabling
     * or re-weighting entries. Pre-wrapped into several rows rather than one auto-wrapping
     * widget - see the class javadoc on {@link ClientTextUtil} for why a fixed-height
     * {@code OptionsList} row makes that the safer choice here - but each of those pre-wrapped
     * lines is still rendered through {@link ScalingLabelWidget} rather than a plain
     * {@code StringWidget}: the wrapping only guarantees a line fits within {@link #ROW_WIDTH}
     * at full size, not within whatever the list's actual visible viewport happens to be, and a
     * line silently losing its tail to that viewport's clip is exactly what made a wrapped
     * sentence look like it lost words between lines. Shrinking every line to guarantee it truly
     * fits removes that failure mode outright.
     */
    private void addWarningRows() {
        for (String line : ClientTextUtil.wrapForRows(font, WARNING.getString(), ROW_WIDTH)) {
            Component styled = Component.literal(line).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            ScalingLabelWidget lineWidget = new ScalingLabelWidget(0, 0, ROW_WIDTH, Button.DEFAULT_HEIGHT, styled, font, ROW_WIDTH, true);
            list.addSmall(lineWidget, null);
        }
    }

    private void addStructureRow(ResourceLocation id, StructureRarityRegistry.Entry entry) {
        // Whether this structure's placement type even supports rarity increases at all
        // (RandomSpreadStructurePlacement only) - fixed for the row's lifetime, computed once
        // rather than re-checked per keystroke, since it depends only on the structure's
        // placement type, never on the value being edited.
        boolean supportsIncrease = StructureRarityRegistry.supportsRarityIncrease(id);

        CommitOnUnfocusEditBox rarityBox = new CommitOnUnfocusEditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, RARITY_LABEL);
        rarityBox.setMaxLength(16);
        rarityBox.setValue(formatRarity(entry.rarity()));
        rarityBox.setTooltip(Tooltip.create(!supportsIncrease && entry.rarity() > StructureRarityRegistry.DEFAULT_RARITY ? RARITY_NO_INCREASE_TOOLTIP : RARITY_TOOLTIP));
        if (!supportsIncrease && entry.rarity() > StructureRarityRegistry.DEFAULT_RARITY) {
            rarityBox.setTextColor(NO_EFFECT_TEXT_COLOR);
        }

        Checkbox enabledCheckbox = Checkbox.builder(Component.empty(), font)
            .selected(entry.enabled())
            .tooltip(Tooltip.create(ENABLED_TOOLTIP))
            .onValueChange((checkbox, value) -> {
                double rarity = parseRarityOrDefault(rarityBox.getValue(), entry.rarity());
                StructureRarityRegistry.save(id, new StructureRarityRegistry.Entry(value, rarity));
            })
            .build();

        rarityBox.setResponder(text -> {
            Double parsed = tryParseRarity(text);
            if (parsed == null) {
                rarityBox.setTextColor(0xFFFF0000);
                rarityBox.setTooltip(Tooltip.create(RARITY_TOOLTIP));
                return;
            }
            // A value above 1.0 for a structure that can't use "extra attempts" (anything not
            // placed via RandomSpreadStructurePlacement, e.g. strongholds) is accepted and
            // saved unchanged - see StructureRarityRegistry#warnAboutUnsupportedRarityIncreases
            // for why it's deliberately not blocked or clamped - but flagged here with a
            // distinct color and tooltip so the player finds out immediately, in-game, instead
            // of the only feedback being a server-log WARN most players never see.
            boolean noEffect = !supportsIncrease && parsed > StructureRarityRegistry.DEFAULT_RARITY;
            rarityBox.setTextColor(noEffect ? NO_EFFECT_TEXT_COLOR : EditBox.DEFAULT_TEXT_COLOR);
            rarityBox.setTooltip(Tooltip.create(noEffect ? RARITY_NO_INCREASE_TOOLTIP : RARITY_TOOLTIP));
            // In-memory only - no disk write per keystroke. See the class javadoc.
            StructureRarityRegistry.applyInMemory(id, new StructureRarityRegistry.Entry(enabledCheckbox.selected(), parsed));
        });

        Runnable commitRarity = () -> {
            Double parsed = tryParseRarity(rarityBox.getValue());
            if (parsed != null) {
                StructureRarityRegistry.save(id, new StructureRarityRegistry.Entry(enabledCheckbox.selected(), parsed));
            }
        };
        rarityBox.setOnUnfocus(commitRarity);
        pendingCommits.add(commitRarity);

        // The id gets its own full-width (310px) row instead of squeezing into the ~150px
        // left-hand column the controls row below uses, and that text is shrunk to guarantee it
        // actually fits (see ScalingLabelWidget) rather than trusting 310px to always be safe -
        // plenty of room for even a long, deeply-nested structure id like
        // "[Pixelmon]: Grotto -> Hills" under normal conditions, and still fully readable (just
        // smaller) on a cramped window instead of silently losing characters to a clip.
        ScalingLabelWidget idLabel = new ScalingLabelWidget(0, 0, ROW_WIDTH, Button.DEFAULT_HEIGHT, ClientTextUtil.formatModdedId(id), font, ROW_WIDTH, false);
        list.addSmall(idLabel, null);

        StructureRarityFieldWidget rightColumn = new StructureRarityFieldWidget(
            0, 0, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, enabledCheckbox, rarityBox
        );
        list.addSmall(rightColumn, null);
    }

    private static String formatRarity(double rarity) {
        return String.format(Locale.ROOT, "%.2f", rarity);
    }

    private static double parseRarityOrDefault(String text, double fallback) {
        Double parsed = tryParseRarity(text);
        return parsed != null ? parsed : fallback;
    }

    private static Double tryParseRarity(String text) {
        double value;
        try {
            value = Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        if (value < StructureRarityRegistry.MIN_RARITY || value > StructureRarityRegistry.MAX_RARITY) {
            return null;
        }
        return value;
    }

    /**
     * An {@link EditBox} that runs a callback when it loses focus - the "commit" point this
     * screen writes an edit to disk at, instead of on every keystroke (see the class javadoc).
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
