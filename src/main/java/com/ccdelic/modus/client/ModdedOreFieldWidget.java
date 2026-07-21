package com.ccdelic.modus.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * One modded ore's editable controls: an "enabled" checkbox plus a frequency-multiplier field
 * and a size-multiplier field, laid out across the row's right-hand column (the left column
 * holds the ore's id, see {@code ModdedOreScreen}). Same composite-widget reasoning and
 * {@code setX}/{@code setY} reflow as {@code StructureRarityFieldWidget} - the row API accepts
 * only two widgets, so the three controls are bundled into this one - just with two text
 * fields sharing the remaining width instead of one. Each field carries its own tooltip so
 * frequency and size are distinguishable despite sitting side by side.
 */
public class ModdedOreFieldWidget extends AbstractContainerWidget {
    private static final int GAP = 4;

    private final Checkbox enabledCheckbox;
    private final EditBox frequencyBox;
    private final EditBox sizeBox;

    public ModdedOreFieldWidget(int x, int y, int width, int height, Checkbox enabledCheckbox, EditBox frequencyBox, EditBox sizeBox) {
        super(x, y, width, height, Component.empty());
        this.enabledCheckbox = enabledCheckbox;
        this.frequencyBox = frequencyBox;
        this.sizeBox = sizeBox;
        updateLayout();
    }

    private void updateLayout() {
        int checkboxWidth = enabledCheckbox.getWidth();
        enabledCheckbox.setX(getX());
        enabledCheckbox.setY(getY() + (getHeight() - enabledCheckbox.getHeight()) / 2);

        int fieldsStart = getX() + checkboxWidth + GAP;
        int fieldsTotal = Math.max(0, getX() + getWidth() - fieldsStart);
        int fieldWidth = Math.max(0, (fieldsTotal - GAP) / 2);

        frequencyBox.setX(fieldsStart);
        frequencyBox.setY(getY());
        frequencyBox.setWidth(fieldWidth);
        frequencyBox.setHeight(getHeight());

        sizeBox.setX(fieldsStart + fieldWidth + GAP);
        sizeBox.setY(getY());
        sizeBox.setWidth(fieldWidth);
        sizeBox.setHeight(getHeight());
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
        return List.of(enabledCheckbox, frequencyBox, sizeBox);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        enabledCheckbox.render(graphics, mouseX, mouseY, partialTick);
        frequencyBox.render(graphics, mouseX, mouseY, partialTick);
        sizeBox.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        frequencyBox.updateNarration(output);
    }
}
