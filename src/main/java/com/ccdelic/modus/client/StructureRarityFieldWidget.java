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
 * One structure's editable controls: an "enabled" checkbox plus a rarity text field, side by
 * side in the row's right-hand column (the left column holds the structure's own id, see
 * {@code StructureRarityScreen}). Modeled on {@code ReplacementFieldWidget} - same reasoning
 * for why a composite widget is needed (the row API only accepts two widgets total) and the
 * same {@code setX}/{@code setY} reflow approach, since {@code OptionsList} only ever
 * repositions row widgets, never resizes them (confirmed by reading
 * {@code OptionsList.Entry#render}).
 * <p>
 * Unlike {@code ReplacementFieldWidget}'s two text fields, {@link Checkbox} computes its own
 * fixed width from its label at construction time and doesn't support being resized
 * afterward - so this reads that width back with {@link Checkbox#getWidth()} once, rather
 * than imposing one, and gives the rarity field whatever room is left.
 */
public class StructureRarityFieldWidget extends AbstractContainerWidget {
    private static final int GAP = 4;

    private final Checkbox enabledCheckbox;
    private final EditBox rarityBox;

    public StructureRarityFieldWidget(int x, int y, int width, int height, Checkbox enabledCheckbox, EditBox rarityBox) {
        super(x, y, width, height, Component.empty());
        this.enabledCheckbox = enabledCheckbox;
        this.rarityBox = rarityBox;
        updateLayout();
    }

    private void updateLayout() {
        int checkboxWidth = enabledCheckbox.getWidth();
        enabledCheckbox.setX(getX());
        enabledCheckbox.setY(getY() + (getHeight() - enabledCheckbox.getHeight()) / 2);

        int rarityX = getX() + checkboxWidth + GAP;
        rarityBox.setX(rarityX);
        rarityBox.setY(getY());
        rarityBox.setWidth(Math.max(0, getWidth() - checkboxWidth - GAP));
        rarityBox.setHeight(getHeight());
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
        return List.of(enabledCheckbox, rarityBox);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        enabledCheckbox.render(graphics, mouseX, mouseY, partialTick);
        rarityBox.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        rarityBox.updateNarration(output);
    }
}
