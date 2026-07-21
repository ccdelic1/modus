package com.ccdelic.modus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Shared text-presentation helpers for {@code ModdedOreScreen} and {@code StructureRarityScreen}:
 * turning a raw {@link ResourceLocation} id into a readable, color-coded label
 * ({@link #formatModdedId}), and greedily word-wrapping a resolved translation string across
 * several centered rows ({@link #wrapForRows}). Both screens lay their content out through
 * {@code OptionsList}, which gives every row a fixed 25px height regardless of a widget's own
 * reported height (see {@code net.minecraft.client.gui.components.OptionsList#DEFAULT_ITEM_HEIGHT}) -
 * so a long warning message is pre-wrapped here into several plain, single-line rows instead of
 * handed to one auto-wrapping multi-line widget, which could render past the bottom of its
 * allotted row into whatever is laid out beneath it.
 */
public final class ClientTextUtil {
    private static final TextColor MOD_NAME_COLOR = TextColor.fromRgb(0xAA00AA);
    private static final TextColor PARENT_SEGMENT_COLOR = TextColor.fromRgb(0xAAAAAA);
    private static final TextColor ARROW_COLOR = TextColor.fromRgb(0xCCCCCC);
    private static final TextColor LEAF_SEGMENT_COLOR = TextColor.fromRgb(0xFFFFFF);

    private ClientTextUtil() {
    }

    /**
     * Renders {@code id} as {@code [Mod Name]: Segment -> Segment -> Leaf Segment}: a purple
     * bold "[Mod Name]:" prefix (the owning mod's display name, falling back to a humanized
     * namespace if the namespace isn't a currently-loaded mod id), followed by the id's path
     * segments humanized ({@code "_"} replaced with spaces, each word capitalized) and joined
     * with light-gray {@code " -> "} arrows - every segment but the last in gray, the last
     * ("leaf") segment in white so the actual ore/structure name stands out from the
     * category/subfolder segments leading up to it. A single-segment path (the common case,
     * e.g. {@code create:zinc_ore}) is rendered as just that one white segment with no arrows.
     */
    public static MutableComponent formatModdedId(ResourceLocation id) {
        MutableComponent result = Component.literal("[" + modDisplayName(id.getNamespace()) + "]: ")
            .withStyle(style -> style.withColor(MOD_NAME_COLOR).withBold(true));

        String[] segments = id.getPath().split("/");
        for (int i = 0; i < segments.length; i++) {
            boolean leaf = i == segments.length - 1;
            TextColor color = leaf ? LEAF_SEGMENT_COLOR : PARENT_SEGMENT_COLOR;
            result.append(Component.literal(humanizeSegment(segments[i])).withStyle(style -> style.withColor(color)));
            if (!leaf) {
                result.append(Component.literal(" -> ").withStyle(style -> style.withColor(ARROW_COLOR)));
            }
        }
        return result;
    }

    /**
     * Greedily word-wraps {@code text} - a translation string already resolved to the player's
     * locale, so the wrapping measures what will actually render, not the translation key -
     * into lines no wider than {@code maxWidth}. A single word that alone exceeds
     * {@code maxWidth} is kept whole on its own line rather than being cut mid-word.
     */
    public static List<String> wrapForRows(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (!currentLine.isEmpty() && font.width(candidate) > maxWidth) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(candidate);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static String modDisplayName(String namespace) {
        return ModList.get().getModContainerById(namespace)
            .map(container -> container.getModInfo().getDisplayName())
            .orElseGet(() -> humanizeSegment(namespace));
    }

    /** Package-visible so {@code MobSpawnCapsScreen} can humanize a bare {@code MobCategory} serialized name the same way, without a mod-namespaced id to work from. */
    static String humanizeSegment(String segment) {
        String[] words = segment.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.isEmpty() ? segment : result.toString();
    }
}
