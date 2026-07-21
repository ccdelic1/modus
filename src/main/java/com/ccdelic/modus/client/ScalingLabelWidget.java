package com.ccdelic.modus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * A non-interactive text label that shrinks its own font scale purely based on how wide its
 * message measures - "shrink the font by how long the string is" - instead of ever silently
 * losing part of the text to clipping. Two things this deliberately does NOT depend on, after
 * both turned out unreliable in practice for this mod's config screens:
 * <ul>
 *   <li>This widget's own declared width, or anything computed for it at construction /
 *   {@code repositionElements()} time. The target width to shrink against is read fresh from
 *   {@link Minecraft#getWindow()} on every single render call, so it can't go stale regardless
 *   of whether some Screen-level resize callback fires as expected.</li>
 *   <li>{@code OptionsList}'s own scissor/viewport clip. That clip only ever removes pixels
 *   silently - if a row is wider than what's actually visible, the cut-off part just vanishes
 *   mid-character with no ellipsis, which for a wrapped multi-line message looks like a sentence
 *   that abruptly loses words between lines. Since {@link #maxTargetWidth} already treats the
 *   live window width as a hard ceiling (see {@link #SAFETY_MARGIN}), the rendered text is
 *   voluntarily shrunk to fit well inside that clip region rather than relying on it to not
 *   trigger.</li>
 * </ul>
 * Scaling only goes down to {@link #MIN_SCALE} - past that floor this falls back to
 * ellipsis-clipping (rendered at {@code MIN_SCALE}, not full size), for the pathological case of
 * a window too small to show anything legible at all.
 */
public class ScalingLabelWidget extends AbstractWidget {
    private static final float MIN_SCALE = 0.4F;
    /** Subtracted from the live window width to leave room for the list's own scrollbar and side padding. */
    private static final int SAFETY_MARGIN = 24;
    private static final int ABSOLUTE_MIN_TARGET_WIDTH = 40;

    private final Font font;
    private final int maxTargetWidth;
    private final boolean centered;

    /**
     * @param maxTargetWidth the ideal width to fit within (e.g. the row's normal width) - never
     *                        exceeded even on a huge window, so this only ever shrinks text, it
     *                        never grows it past its natural size.
     * @param centered        whether the (possibly shrunk) text should be centered within this
     *                        widget's own declared bounds, or left-aligned.
     */
    public ScalingLabelWidget(int x, int y, int width, int height, Component message, Font font, int maxTargetWidth, boolean centered) {
        super(x, y, width, height, message);
        this.font = font;
        this.maxTargetWidth = maxTargetWidth;
        this.centered = centered;
        this.active = false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Component message = getMessage();
        int fullWidth = font.width(message);

        int liveWindowWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int target = Math.max(ABSOLUTE_MIN_TARGET_WIDTH, Math.min(maxTargetWidth, liveWindowWidth - SAFETY_MARGIN));

        float scale = 1.0F;
        FormattedCharSequence sequence = message.getVisualOrderText();
        if (fullWidth > target) {
            float neededScale = (float) target / fullWidth;
            if (neededScale >= MIN_SCALE) {
                scale = neededScale;
            } else {
                scale = MIN_SCALE;
                sequence = clip(message, (int) (target / MIN_SCALE));
            }
        }

        int scaledWidth = Math.round(font.width(sequence) * scale);
        int drawX = centered ? getX() + Math.round((getWidth() - scaledWidth) / 2.0F) : getX();
        int drawY = getY() + Math.round((getHeight() - 9 * scale) / 2.0F);

        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, sequence, 0, 0, 0xFFFFFF);
        graphics.pose().popPose();
    }

    private FormattedCharSequence clip(Component message, int maxWidth) {
        int budget = Math.max(0, maxWidth - font.width(CommonComponents.ELLIPSIS));
        FormattedText clipped = font.substrByWidth(message, budget);
        return Language.getInstance().getVisualOrder(FormattedText.composite(clipped, CommonComponents.ELLIPSIS));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
