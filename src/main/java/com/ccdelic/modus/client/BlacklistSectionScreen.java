package com.ccdelic.modus.client;

import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The section screen for {@code structureOptions/blacklist.toml}. Its only job is routing
 * the "blacklist" list field to {@link BlacklistListScreen} instead of NeoForge's stock
 * {@code ConfigurationListScreen} - {@link #createList} is the officially documented
 * extension point for exactly this ("To change the way lists work, override createList(...)
 * and return a subclassed ConfigurationListScreen", per {@code ConfigurationSectionScreen}'s
 * own class javadoc). Every other field (currently just "enabled") falls through to the
 * default behavior unchanged.
 */
public class BlacklistSectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {
    private static final String BLACKLIST_KEY = "blacklist";

    public BlacklistSectionScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> Element createList(String key, ModConfigSpec.ListValueSpec spec, ModConfigSpec.ConfigValue<List<T>> list) {
        if (!BLACKLIST_KEY.equals(key)) {
            return super.createList(key, spec, list);
        }

        Component label = getTranslationComponent(key);
        Component tooltip = getTooltipComponent(key, null);
        Component screenTitle = Component.empty().append(this.getTitle()).append(" > ").append(label);
        ModConfigSpec.ConfigValue<List<String>> stringList = (ModConfigSpec.ConfigValue<List<String>>) (ModConfigSpec.ConfigValue<?>) list;

        return new Element(
            label,
            tooltip,
            Button.builder(
                    label,
                    button -> {
                        BlacklistListScreen listScreen = (BlacklistListScreen) sectionCache.computeIfAbsent(
                            key,
                            k -> new BlacklistListScreen(Context.list(context, this), key, screenTitle, spec, stringList)
                        );
                        minecraft.setScreen(listScreen.rebuild());
                    }
                )
                .tooltip(Tooltip.create(tooltip))
                .width(Button.DEFAULT_WIDTH)
                .build(),
            false
        );
    }
}
