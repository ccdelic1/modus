package com.ccdelic.modus.client;

import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Replaces NeoForge's stock generic list editor for the structure block blacklist with a
 * two-column layout matching what the config actually stores: each entry is a
 * "&lt;removed block&gt; &lt;replacement block&gt;" pair, not one opaque string. The stock
 * editor (a single text box per entry, prefixed with its index, with reorder/delete controls
 * sandwiched between the index and the field) has no way to express that structure, so this
 * overrides the officially documented extension points -
 * {@link ConfigurationScreen.ConfigurationListScreen#rebuild()} adds a header row before the
 * per-entry loop and replaces the generic per-entry dispatch with
 * {@link #addBlacklistRow(int, String)} - rather than trying to bend the generic string-list
 * UI to fit.
 * <p>
 * Everything else (add/undo/reset buttons, saving {@link #cfgList} back to the underlying
 * {@code ConfigValue} on close, the size-unbounded validation) is inherited unchanged from
 * {@link ConfigurationScreen.ConfigurationListScreen} - only how each entry is displayed and
 * edited is different.
 */
public class BlacklistListScreen extends ConfigurationScreen.ConfigurationListScreen<String> {
    private static final Component REMOVED_BLOCK_LABEL = Component.translatable("modus.configuration.blacklist.removedBlock");
    private static final Component REPLACEMENT_LABEL = Component.translatable("modus.configuration.blacklist.replacement");
    private static final int MAX_BLOCK_ID_LENGTH = 256;

    public BlacklistListScreen(
        Context context,
        String key,
        Component title,
        ModConfigSpec.ListValueSpec spec,
        ModConfigSpec.ConfigValue<List<String>> valueList
    ) {
        super(context, key, title, spec, valueList);
    }

    /**
     * Widened from {@code protected} to {@code public}: {@link BlacklistSectionScreen} lives
     * in a different package than the vanilla NeoForge classes this extends, and needs to
     * call this directly on a screen it just constructed (matching how NeoForge's own
     * {@code createList} calls {@code .rebuild()} the same way, which only compiles for it
     * because it's in the same package as {@code ConfigurationSectionScreen}).
     */
    @Override
    public ConfigurationSectionScreen rebuild() {
        if (list != null) {
            list.children().clear();

            list.addSmall(
                new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, REMOVED_BLOCK_LABEL, font).alignLeft(),
                new StringWidget(Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, REPLACEMENT_LABEL, font).alignLeft()
            );

            for (int idx = 0; idx < cfgList.size(); idx++) {
                addBlacklistRow(idx, cfgList.get(idx));
            }

            createAddElementButton();
            if (undoButton == null) {
                createUndoButton();
                createResetButton();
            }
        }
        return this;
    }

    private void addBlacklistRow(int idx, String rawEntry) {
        String[] parts = splitEntry(rawEntry);

        EditBox removedBox = new EditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, REMOVED_BLOCK_LABEL);
        removedBox.setEditable(true);
        removedBox.setMaxLength(MAX_BLOCK_ID_LENGTH);
        removedBox.setValue(parts[0]);
        removedBox.setTooltip(Tooltip.create(REMOVED_BLOCK_LABEL));

        EditBox replacementBox = new EditBox(font, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, REPLACEMENT_LABEL);
        replacementBox.setEditable(true);
        replacementBox.setMaxLength(MAX_BLOCK_ID_LENGTH);
        replacementBox.setValue(parts[1]);
        replacementBox.setTooltip(Tooltip.create(REPLACEMENT_LABEL));

        Runnable commit = () -> {
            cfgList.set(idx, combineEntry(removedBox.getValue(), replacementBox.getValue()));
            onChanged(key);
        };
        removedBox.setResponder(v -> commit.run());
        replacementBox.setResponder(v -> commit.run());

        Button deleteButton = Button.builder(ConfigurationScreen.REMOVE_LIST_ELEMENT, button -> del(idx, false))
            .width(Button.DEFAULT_HEIGHT)
            .build();

        ReplacementFieldWidget rightColumn = new ReplacementFieldWidget(
            0, 0, Button.DEFAULT_WIDTH, Button.DEFAULT_HEIGHT, replacementBox, deleteButton
        );

        list.addSmall(removedBox, rightColumn);
    }

    private static String[] splitEntry(String rawEntry) {
        String trimmed = rawEntry == null ? "" : rawEntry.trim();
        if (trimmed.isEmpty()) {
            return new String[] {"", ""};
        }
        String[] parts = trimmed.split("\\s+", 2);
        return parts.length == 2 ? parts : new String[] {parts[0], ""};
    }

    private static String combineEntry(String removed, String replacement) {
        return removed.trim() + " " + replacement.trim();
    }
}
