package com.ccdelic.modus.client;

import com.ccdelic.modus.OreType;
import com.ccdelic.modus.config.OreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;

/**
 * Lists all eleven vanilla per-ore settings pages ("Coal Ore Settings...", "Iron Ore
 * Settings...", "Ancient Debris Settings...", etc.) behind Modus's own "Vanilla Ore
 * Options..." button on {@link ModusRootScreen}, instead of each one sitting directly in the
 * root config screen's flat list alongside Mob Spawn Caps, XP Settings, and the rest. Each
 * button opens {@link ModusSectionScreen} for that ore's real {@link OreConfig} - the exact
 * same per-value editing screen (with the same confirmation-gated Reset) NeoForge's own
 * generic system would have shown for it directly; only where the button lives changed.
 */
public class VanillaOreOptionsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("modus.configuration.section.modus.oreoptions.vanillaoremenu.toml.title");
    private static final int ROW_WIDTH = 310;

    public VanillaOreOptionsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, TITLE);
    }

    @Override
    protected void addOptions() {
        for (OreType ore : OreType.values()) {
            // Mirrors NeoForge's own ConfigurationScreen#translatableConfig key derivation for
            // this mod's own per-ore files (config/Modus/oreOptions/<ore>.toml): every
            // non-alphanumeric character in the ore id becomes ".", which for these specific
            // ids only ever means the underscore in "nether_gold" / "ancient_debris" -
            // reusing the very same lang keys ConfigurationScreen would already have used for
            // these ores' buttons, rather than inventing new ones.
            String baseKey = "modus.configuration.section.modus.oreoptions." + ore.getSerializedName().replace('_', '.') + ".toml";
            // A lookup failure here must only skip this one ore's button, not the other ten -
            // see ModConfigLookup's javadoc for why this is defensive rather than expected.
            ModConfig modConfig = ModConfigLookup.find(OreConfig.spec(ore));
            if (modConfig == null) {
                continue;
            }
            Component label = Component.translatable("neoforge.configuration.uitext.section", Component.translatable(baseKey));
            Component title = Component.translatable(baseKey + ".title");

            list.addSmall(
                Button.builder(label, button -> minecraft.setScreen(new ModusSectionScreen(this, ModConfig.Type.COMMON, modConfig, title)))
                    .width(ROW_WIDTH)
                    .build(),
                null
            );
        }
    }
}
