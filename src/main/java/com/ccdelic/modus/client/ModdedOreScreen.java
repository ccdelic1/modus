package com.ccdelic.modus.client;

import com.ccdelic.modus.oregen.ModdedOreRegistry;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Lists every modded ore {@link ModdedOreRegistry} currently knows about, each with an
 * "enabled" checkbox, a frequency-multiplier field and a size-multiplier field - the in-game
 * equivalent of hand-editing the TOML files under
 * {@code config/Modus/oreOptions/modded/<Mod Name>/}. Vanilla ores are deliberately NOT here:
 * they keep their own per-ore sections in NeoForge's config screen (Coal Ore Settings, etc.),
 * since they use the absolute-value {@code ModConfigSpec} system, not this multiplier one.
 * <p>
 * Reached as the "Modded Ore Options" button in NeoForge's {@code ConfigurationScreen} list
 * via {@code ModusClient#sectionScreenFor} recognizing
 * {@link com.ccdelic.modus.oregen.ModdedOreMenuMarker}'s marker spec - the same mechanism
 * {@code StructureRarityScreen} uses, for the same reason (modded ores are discovered
 * dynamically after data packs load, too late for a real {@code ModConfigSpec}).
 * <p>
 * Populated before any world loads by {@link WorldgenConfigBootstrap}; if it's empty here,
 * either no ore mod is installed or the mod places its ore via a fully custom feature that
 * auto-detection can't recognize (see {@link ModdedOreRegistry} - turn on debug logging to
 * see which of a mod's features were skipped). Edits save immediately, one file at a time.
 */
public class ModdedOreScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("modus.configuration.moddedOre.title");
    private static final Component EMPTY_MESSAGE = Component.translatable("modus.configuration.moddedOre.empty");
    private static final Component HEADER = Component.translatable("modus.configuration.moddedOre.header");
    private static final Component ENABLED_TOOLTIP = Component.translatable("modus.configuration.moddedOre.enabled.tooltip");
    private static final Component FREQUENCY_LABEL = Component.translatable("modus.configuration.moddedOre.frequency");
    private static final Component FREQUENCY_TOOLTIP = Component.translatable("modus.configuration.moddedOre.frequency.tooltip");
    private static final Component SIZE_LABEL = Component.translatable("modus.configuration.moddedOre.size");
    private static final Component SIZE_TOOLTIP = Component.translatable("modus.configuration.moddedOre.size.tooltip");
    private static final int ROW_WIDTH = 310;

    public ModdedOreScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addOptions() {
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

    private void addOreRow(ResourceLocation id, ModdedOreRegistry.Entry entry) {
        EditBox frequencyBox = new EditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, FREQUENCY_LABEL);
        frequencyBox.setMaxLength(16);
        frequencyBox.setValue(format(entry.frequencyMultiplier()));
        frequencyBox.setTooltip(Tooltip.create(FREQUENCY_TOOLTIP));

        EditBox sizeBox = new EditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, SIZE_LABEL);
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
                saveRow(id, enabledCheckbox.selected(), frequencyBox, sizeBox, entry);
            }
        });
        sizeBox.setResponder(text -> {
            boolean valid = tryParse(text, ModdedOreRegistry.MIN_SIZE_MULTIPLIER, ModdedOreRegistry.MAX_SIZE_MULTIPLIER) != null;
            sizeBox.setTextColor(valid ? EditBox.DEFAULT_TEXT_COLOR : 0xFFFF0000);
            if (valid) {
                saveRow(id, enabledCheckbox.selected(), frequencyBox, sizeBox, entry);
            }
        });

        StringWidget idLabel = new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, Component.literal(id.toString()), font).alignLeft();
        ModdedOreFieldWidget rightColumn = new ModdedOreFieldWidget(
            0, 0, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, enabledCheckbox, frequencyBox, sizeBox
        );

        list.addSmall(idLabel, rightColumn);
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
}
