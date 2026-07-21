package com.ccdelic.modus;

import com.mojang.logging.LogUtils;
import com.ccdelic.modus.config.MobConfig;
import com.ccdelic.modus.config.OreConfig;
import com.ccdelic.modus.config.XpConfig;
import com.ccdelic.modus.mobs.MobSpawnCapHandler;
import com.ccdelic.modus.registry.ModBiomeModifiers;
import com.ccdelic.modus.registry.ModFeatures;
import com.ccdelic.modus.registry.ModPlacementModifierTypes;
import com.ccdelic.modus.structures.StructureBlacklistConfig;
import com.ccdelic.modus.structures.StructureRarityRegistry;
import com.ccdelic.modus.xp.XpGainHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Makes ore world generation (vein size, veins-per-chunk, rarity, height range), mob spawn
 * caps (per {@link net.minecraft.world.entity.MobCategory}), player XP gain rate, structure
 * rarity/spawn-disabling, and structure block replacement fully configurable.
 * <p>
 * Every feature is opt-in: {@link OreConfig}, {@link MobConfig}, {@link XpConfig} and
 * {@link StructureBlacklistConfig} all default their master switch to {@code false}, so a
 * fresh install with no config changes plays identically to vanilla. The one exception is
 * per-structure rarity ({@link StructureRarityRegistry}), whose default ({@code enabled =
 * true, rarity = 1.0}) is already a no-op in its own right - {@code false} there means
 * "actively remove this structure", not "Modus is doing something", so flipping it to match
 * the others would have made structures vanish by default instead of leaving them alone.
 * <p>
 * Config is split into many files under {@code config/Modus/}: one TOML per ore under
 * {@code oreOptions/}, a single TOML under {@code mobOptions/} for mob spawn caps, a single
 * TOML under {@code XP/} for the XP multiplier, one TOML per structure under
 * {@code structureOptions/vanilla/} or {@code structureOptions/modded/<Mod Name>/} for
 * per-structure rarity, and a single TOML at {@code structureOptions/blacklist.toml} for
 * structure block replacement. See {@link OreConfig}, {@link MobConfig}, {@link XpConfig},
 * {@link StructureRarityRegistry} and {@link StructureBlacklistConfig} for the actual values,
 * and {@link com.ccdelic.modus.worldgen} / {@link MobSpawnCapHandler} / {@link XpGainHandler}
 * / {@code com.ccdelic.modus.mixin} for how they're wired in. Ore generation's on/off switch
 * specifically is wired through {@link com.ccdelic.modus.worldgen.ConfigurableOreBiomeModifier}
 * rather than checked live during generation the way every other value here is - see that
 * class's javadoc for why turning an ore's generation off needs to be able to hand control
 * back to vanilla's own generation, not just generate nothing.
 */
@Mod(Modus.MODID)
public class Modus {
    public static final String MODID = "modus";
    public static final String CONFIG_FOLDER = "Modus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Modus(IEventBus modEventBus, ModContainer modContainer) {
        ModFeatures.FEATURES.register(modEventBus);
        ModPlacementModifierTypes.PLACEMENT_MODIFIER_TYPES.register(modEventBus);
        ModBiomeModifiers.BIOME_MODIFIER_SERIALIZERS.register(modEventBus);

        for (OreType ore : OreType.values()) {
            String fileName = CONFIG_FOLDER + "/oreOptions/" + ore.getSerializedName() + ".toml";
            modContainer.registerConfig(ModConfig.Type.COMMON, OreConfig.spec(ore), fileName);
        }
        modContainer.registerConfig(ModConfig.Type.COMMON, MobConfig.SPEC, CONFIG_FOLDER + "/mobOptions/mobs.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, XpConfig.SPEC, CONFIG_FOLDER + "/XP/xp.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, StructureBlacklistConfig.SPEC, CONFIG_FOLDER + "/structureOptions/blacklist.toml");

        LOGGER.info(
            "Modus loaded - ore generation, mob spawn caps, XP gain rate, structure rarity and structure block "
                + "replacement are now driven by config"
        );
    }
}
