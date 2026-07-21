package com.ccdelic.modus.registry;

import com.ccdelic.modus.Modus;
import com.ccdelic.modus.worldgen.ConfigurableOreConfiguration;
import com.ccdelic.modus.worldgen.ConfigurableOreFeature;
import com.ccdelic.modus.worldgen.ConfigurableScatteredOreFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Modus.MODID);

    public static final DeferredHolder<Feature<?>, ConfigurableOreFeature> CONFIGURABLE_ORE = FEATURES.register(
        "configurable_ore", () -> new ConfigurableOreFeature(ConfigurableOreConfiguration.CODEC)
    );

    public static final DeferredHolder<Feature<?>, ConfigurableScatteredOreFeature> CONFIGURABLE_SCATTERED_ORE = FEATURES.register(
        "configurable_scattered_ore", () -> new ConfigurableScatteredOreFeature(ConfigurableOreConfiguration.CODEC)
    );

    private ModFeatures() {
    }
}
