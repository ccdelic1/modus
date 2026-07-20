package com.ccdelic.modus.client;

import com.ccdelic.modus.structures.StructureRarityRegistry;
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
 * Lists every structure {@link StructureRarityRegistry} currently knows about, with an
 * "enabled" checkbox and a rarity field for each - the in-game equivalent of hand-editing
 * the TOML files under {@code config/Modus/structureOptions/vanilla/} and {@code
 * .../modded/<Mod Name>/}.
 * <p>
 * Reached directly as the "Structure Rarity Options" button in NeoForge's
 * {@code ConfigurationScreen} button list, alongside "Structure Block Blacklist" and the
 * rest, via {@code ModusClient#sectionScreenFor} recognizing {@code
 * com.ccdelic.modus.structures.StructureRarityMenuMarker}'s intentionally empty spec and
 * returning this instead of the (otherwise empty) default section screen that spec would
 * normally lead to - see that marker class's javadoc for why a real {@code ModConfigSpec}
 * couldn't represent this directly.
 * <p>
 * Normally empty until a world has loaded at least once this session, since the structure
 * list comes from {@code Registries.STRUCTURE}, which doesn't exist until a server
 * (singleplayer or dedicated) has actually started - {@link WorldgenConfigBootstrap} exists
 * specifically to close that gap by resolving a stand-in registry in the background before
 * that happens, so in practice this is usually already populated well before a player
 * navigates here. Edits save immediately, one file at a time, via
 * {@link StructureRarityRegistry#save} - there's no batch/undo system here the way NeoForge's
 * own config screens have, since this data already hot-reloads independently of any of that.
 */
public class StructureRarityScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("modus.configuration.structureRarity.title");
    private static final Component EMPTY_MESSAGE = Component.translatable("modus.configuration.structureRarity.empty");
    private static final Component ENABLED_TOOLTIP = Component.translatable("modus.configuration.structureRarity.enabled.tooltip");
    private static final Component RARITY_LABEL = Component.translatable("modus.configuration.rarity.short");
    private static final Component RARITY_TOOLTIP = Component.translatable("modus.configuration.structureRarity.rarity.tooltip");
    private static final int ROW_WIDTH = 310;

    public StructureRarityScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addOptions() {
        Map<ResourceLocation, StructureRarityRegistry.Entry> entries = StructureRarityRegistry.allEntries();
        if (entries.isEmpty()) {
            list.addSmall(new StringWidget(ROW_WIDTH, Button.DEFAULT_HEIGHT, EMPTY_MESSAGE, font).alignLeft(), null);
            return;
        }

        entries.keySet().stream()
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .forEach(id -> addStructureRow(id, entries.get(id)));
    }

    private void addStructureRow(ResourceLocation id, StructureRarityRegistry.Entry entry) {
        EditBox rarityBox = new EditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, RARITY_LABEL);
        rarityBox.setMaxLength(16);
        rarityBox.setValue(formatRarity(entry.rarity()));
        rarityBox.setTooltip(Tooltip.create(RARITY_TOOLTIP));

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
                return;
            }
            rarityBox.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
            StructureRarityRegistry.save(id, new StructureRarityRegistry.Entry(enabledCheckbox.selected(), parsed));
        });

        StringWidget idLabel = new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, Component.literal(id.toString()), font).alignLeft();
        StructureRarityFieldWidget rightColumn = new StructureRarityFieldWidget(
            0, 0, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, enabledCheckbox, rarityBox
        );

        list.addSmall(idLabel, rightColumn);
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
}
