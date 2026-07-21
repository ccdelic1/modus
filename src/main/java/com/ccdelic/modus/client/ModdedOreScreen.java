package com.ccdelic.modus.client;

import com.ccdelic.modus.oregen.ModdedOreRegistry;
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
 * Lists every modded ore {@link ModdedOreRegistry} currently knows about, each with an
 * "enabled" checkbox, a frequency-multiplier field and a size-multiplier field - the in-game
 * equivalent of hand-editing the TOML files under
 * {@code config/Modus/oreOptions/modded/<Mod Name>/}. Vanilla ores are deliberately NOT here:
 * they keep their own per-ore sections in NeoForge's config screen (Coal Ore Settings, etc.),
 * since they use the absolute-value {@code ModConfigSpec} system, not this multiplier one.
 * <p>
 * Reached as the "Modded Ore Options" button on {@code ModusRootScreen}, constructed straight
 * from there - the same reason {@code StructureRarityScreen} is (modded ores are discovered
 * dynamically after data packs load, too late for a real {@code ModConfigSpec}).
 * <p>
 * Populated before any world loads by {@link WorldgenConfigBootstrap}; if it's empty here,
 * either no ore mod is installed or the mod places its ore via a fully custom feature that
 * auto-detection can't recognize (see {@link ModdedOreRegistry} - turn on debug logging to
 * see which of a mod's features were skipped).
 * <p>
 * Each text field applies its value in memory on every keystroke (so world generation and
 * this screen's own display see it immediately - {@link ModdedOreRegistry#applyInMemory}),
 * but only writes it to disk ({@link ModdedOreRegistry#save}) once the edit is committed: a
 * field losing focus, the enabled checkbox being toggled, or this screen closing (see
 * {@link #removed()}, a safety net in case a field is still focused when the player backs
 * out). {@code CommentedFileConfig}'s {@code sync()} option flushes to disk on every
 * {@code save()} call, so writing on every keystroke instead of every commit would mean
 * several full file-open/write/sync/close cycles for one intended edit.
 */
public class ModdedOreScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("modus.configuration.moddedOre.title");
    private static final Component WARNING = Component.translatable("modus.configuration.moddedOre.warning");
    private static final Component EMPTY_MESSAGE = Component.translatable("modus.configuration.moddedOre.empty");
    private static final Component HEADER = Component.translatable("modus.configuration.moddedOre.header");
    private static final Component ENABLED_TOOLTIP = Component.translatable("modus.configuration.moddedOre.enabled.tooltip");
    private static final Component FREQUENCY_LABEL = Component.translatable("modus.configuration.moddedOre.frequency");
    private static final Component FREQUENCY_TOOLTIP = Component.translatable("modus.configuration.moddedOre.frequency.tooltip");
    private static final Component SIZE_LABEL = Component.translatable("modus.configuration.moddedOre.size");
    private static final Component SIZE_TOOLTIP = Component.translatable("modus.configuration.moddedOre.size.tooltip");
    private static final int ROW_WIDTH = 310;

    private final List<Runnable> pendingCommits = new ArrayList<>();

    public ModdedOreScreen(Screen parent) {
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
     * next to it that resets every known modded ore back to {@link ModdedOreRegistry.Entry#DEFAULT}
     * (enabled, 1.0x frequency, 1.0x size) after the player confirms via {@link ResetConfirmation}.
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
     * Resets every currently-known modded ore to {@link ModdedOreRegistry.Entry#DEFAULT} and
     * repopulates {@link #list} in place so every row immediately reflects the reset values.
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
     * reason: it's about to be repopulated from scratch by {@link #addOreRow}, and the entries
     * from before the reset would otherwise also still fire, against now-discarded widgets, the
     * next time this screen closes.
     */
    private void resetAll() {
        for (ResourceLocation id : ModdedOreRegistry.allEntries().keySet()) {
            ModdedOreRegistry.save(id, ModdedOreRegistry.Entry.DEFAULT);
        }
        pendingCommits.clear();
        list.children().clear();
        addOptions();
    }

    @Override
    protected void addOptions() {
        addWarningRows();

        Map<ResourceLocation, ModdedOreRegistry.Entry> entries = ModdedOreRegistry.allEntries();
        if (entries.isEmpty()) {
            list.addSmall(new StringWidget(ROW_WIDTH, Button.DEFAULT_HEIGHT, EMPTY_MESSAGE, font).alignLeft(), null);
            return;
        }

        list.addSmall(new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, Component.empty(), font), null);
        list.addSmall(new StringWidget(ROW_WIDTH, Button.DEFAULT_HEIGHT, HEADER, font).alignLeft(), null);

        entries.keySet().stream()
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .forEach(id -> addOreRow(id, entries.get(id)));
    }

    /**
     * Auto-detection (see {@link ModdedOreRegistry#isOre}) is a heuristic, not a guarantee - it
     * gets to ~99%+ accuracy but can still misclassify a mod's non-ore blob feature as an ore.
     * Pre-wrapped into several rows rather than one auto-wrapping widget - see the class javadoc
     * on {@link ClientTextUtil} for why a fixed-height {@code OptionsList} row makes that the
     * safer choice here - but each of those pre-wrapped lines is still rendered through
     * {@link ScalingLabelWidget} rather than a plain {@code StringWidget}: the wrapping only
     * guarantees a line fits within {@link #ROW_WIDTH} at full size, not within whatever the
     * list's actual visible viewport happens to be, and a line silently losing its tail to that
     * viewport's clip is exactly what made a wrapped sentence look like it lost words between
     * lines. Shrinking every line to guarantee it truly fits removes that failure mode outright.
     */
    private void addWarningRows() {
        for (String line : ClientTextUtil.wrapForRows(font, WARNING.getString(), ROW_WIDTH)) {
            Component styled = Component.literal(line).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            ScalingLabelWidget lineWidget = new ScalingLabelWidget(0, 0, ROW_WIDTH, Button.DEFAULT_HEIGHT, styled, font, ROW_WIDTH, true);
            list.addSmall(lineWidget, null);
        }
    }

    private void addOreRow(ResourceLocation id, ModdedOreRegistry.Entry entry) {
        CommitOnUnfocusEditBox frequencyBox = new CommitOnUnfocusEditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, FREQUENCY_LABEL);
        frequencyBox.setMaxLength(16);
        frequencyBox.setValue(format(entry.frequencyMultiplier()));
        frequencyBox.setTooltip(Tooltip.create(FREQUENCY_TOOLTIP));

        CommitOnUnfocusEditBox sizeBox = new CommitOnUnfocusEditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, SIZE_LABEL);
        sizeBox.setMaxLength(16);
        sizeBox.setValue(format(entry.sizeMultiplier()));
        sizeBox.setTooltip(Tooltip.create(SIZE_TOOLTIP));

        Checkbox enabledCheckbox = Checkbox.builder(Component.empty(), font)
            .selected(entry.enabled())
            .tooltip(Tooltip.create(ENABLED_TOOLTIP))
            .onValueChange((checkbox, value) -> saveRow(id, value, frequencyBox, sizeBox, entry))
            .build();

        frequencyBox.setResponder(text -> {
            boolean valid = tryParse(text, ModdedOreRegistry.MIN_FREQUENCY, ModdedOreRegistry.MAX_FREQUENCY) != null;
            frequencyBox.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : 0xFFFF0000);
            if (valid) {
                // In-memory only - no disk write per keystroke. See the class javadoc.
                applyRowInMemory(id, enabledCheckbox.selected(), frequencyBox, sizeBox, entry);
            }
        });
        sizeBox.setResponder(text -> {
            boolean valid = tryParse(text, ModdedOreRegistry.MIN_SIZE_MULTIPLIER, ModdedOreRegistry.MAX_SIZE_MULTIPLIER) != null;
            sizeBox.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : 0xFFFF0000);
            if (valid) {
                // In-memory only - no disk write per keystroke. See the class javadoc.
                applyRowInMemory(id, enabledCheckbox.selected(), frequencyBox, sizeBox, entry);
            }
        });

        Runnable commitRow = () -> saveRow(id, enabledCheckbox.selected(), frequencyBox, sizeBox, entry);
        frequencyBox.setOnUnfocus(commitRow);
        sizeBox.setOnUnfocus(commitRow);
        pendingCommits.add(commitRow);

        // The id gets its own full-width (310px) row instead of squeezing into the ~150px
        // left-hand column the controls row below uses, and that text is shrunk to guarantee it
        // actually fits (see ScalingLabelWidget) rather than trusting 310px to always be safe -
        // plenty of room for even a long, deeply-nested modded id like
        // "[Pixelmon]: Ores -> Sapphire Ore" under normal conditions, and still fully readable
        // (just smaller) on a cramped window instead of silently losing characters to a clip.
        ScalingLabelWidget idLabel = new ScalingLabelWidget(0, 0, ROW_WIDTH, Button.DEFAULT_HEIGHT, ClientTextUtil.formatModdedId(id), font, ROW_WIDTH, false);
        // Still wrapped in a tooltip as a safety net for the rare id long enough to hit even
        // this widget's own ellipsis-clipping floor - shows the full, untruncated raw id.
        idLabel.setTooltip(Tooltip.create(Component.literal(id.toString())));
        list.addSmall(idLabel, null);

        ModdedOreFieldWidget rightColumn = new ModdedOreFieldWidget(
            0, 0, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, enabledCheckbox, frequencyBox, sizeBox
        );
        list.addSmall(rightColumn, null);
    }

    private static void saveRow(ResourceLocation id, boolean enabled, EditBox frequencyBox, EditBox sizeBox, ModdedOreRegistry.Entry current) {
        Double frequency = tryParse(frequencyBox.getValue(), ModdedOreRegistry.MIN_FREQUENCY, ModdedOreRegistry.MAX_FREQUENCY);
        Double size = tryParse(sizeBox.getValue(), ModdedOreRegistry.MIN_SIZE_MULTIPLIER, ModdedOreRegistry.MAX_SIZE_MULTIPLIER);
        ModdedOreRegistry.save(id, new ModdedOreRegistry.Entry(
            enabled,
            frequency != null ? frequency : current.frequencyMultiplier(),
            size != null ? size : current.sizeMultiplier()
        ));
    }

    private static void applyRowInMemory(ResourceLocation id, boolean enabled, EditBox frequencyBox, EditBox sizeBox, ModdedOreRegistry.Entry current) {
        Double frequency = tryParse(frequencyBox.getValue(), ModdedOreRegistry.MIN_FREQUENCY, ModdedOreRegistry.MAX_FREQUENCY);
        Double size = tryParse(sizeBox.getValue(), ModdedOreRegistry.MIN_SIZE_MULTIPLIER, ModdedOreRegistry.MAX_SIZE_MULTIPLIER);
        ModdedOreRegistry.applyInMemory(id, new ModdedOreRegistry.Entry(
            enabled,
            frequency != null ? frequency : current.frequencyMultiplier(),
            size != null ? size : current.sizeMultiplier()
        ));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Double tryParse(String text, double min, double max) {
        double value;
        try {
            value = Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
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
