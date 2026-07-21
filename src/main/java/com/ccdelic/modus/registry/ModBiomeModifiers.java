package com.ccdelic.modus.registry;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ccdelic.modus.Modus;
import com.ccdelic.modus.OreType;
import com.ccdelic.modus.worldgen.ConfigurableOreBiomeModifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModBiomeModifiers {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Modus.MODID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ConfigurableOreBiomeModifier>> CONFIGURABLE_ORE =
        BIOME_MODIFIER_SERIALIZERS.register(
            "configurable_ore",
            () -> RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        OreType.CODEC.fieldOf("ore").forGetter(ConfigurableOreBiomeModifier::ore),
                        Biome.LIST_CODEC.fieldOf("biomes").forGetter(ConfigurableOreBiomeModifier::biomes),
                        PlacedFeature.LIST_CODEC.fieldOf("remove").forGetter(ConfigurableOreBiomeModifier::remove),
                        PlacedFeature.LIST_CODEC.fieldOf("add").forGetter(ConfigurableOreBiomeModifier::add),
                        Decoration.CODEC.fieldOf("step").forGetter(ConfigurableOreBiomeModifier::step)
                    )
                    .apply(instance, ConfigurableOreBiomeModifier::new)
            )
        );

    private ModBiomeModifiers() {
    }
}
