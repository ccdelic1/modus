package com.ccdelic.modus.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * The right-hand half of a structure blacklist row: the "Replacement" block field plus a
 * delete button pinned to the far right edge, so the delete control never sits between the
 * two text fields the way NeoForge's stock per-element controls (index label, reorder
 * arrows, delete) do when placed in the row's left column.
 * <p>
 * Modeled on {@code ConfigurationScreen.ConfigurationListScreen.ListLabelWidget} - a fixed
 * width is chosen once at construction (the containing {@code OptionsList} only ever calls
 * {@code setPosition}, never {@code setWidth}, on row widgets - confirmed by reading
 * {@code OptionsList.Entry#render}), and {@link #setX} / {@link #setY} reflow the two
 * children whenever the row repositions this widget.
 */
public class ReplacementFieldWidget extends AbstractContainerWidget {
    private static final int GAP = 2;

    private final EditBox replacementBox;
    private final Button deleteButton;
    private final int deleteButtonWidth;

    public ReplacementFieldWidget(int x, int y, int width, int height, EditBox replacementBox, Button deleteButton) {
        super(x, y, width, height, Component.empty());
        this.replacementBox = replacementBox;
        this.deleteButton = deleteButton;
        this.deleteButtonWidth = height;
        updateLayout();
    }

    private void updateLayout() {
        int boxWidth = Math.max(0, getWidth() - deleteButtonWidth - GAP);
        replacementBox.setX(getX());
        replacementBox.setY(getY());
        replacementBox.setWidth(boxWidth);
        replacementBox.setHeight(getHeight());

        deleteButton.setX(getX() + boxWidth + GAP);
        deleteButton.setY(getY());
        deleteButton.setWidth(deleteButtonWidth);
        deleteButton.setHeight(getHeight());
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        updateLayout();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        updateLayout();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(replacementBox, deleteButton);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        replacementBox.render(graphics, mouseX, mouseY, partialTick);
        deleteButton.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        replacementBox.updateNarration(output);
    }
}
