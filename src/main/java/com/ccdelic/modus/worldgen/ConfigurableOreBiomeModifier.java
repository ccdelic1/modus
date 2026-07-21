package com.ccdelic.modus.worldgen;

import com.mojang.serialization.MapCodec;
import com.ccdelic.modus.OreType;
import com.ccdelic.modus.config.OreConfig;
import com.ccdelic.modus.registry.ModBiomeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Replaces vanilla's own {@code neoforge:remove_features} + {@code neoforge:add_features}
 * pair - previously used to unconditionally strip an ore's vanilla placed features and
 * splice in {@link ConfigurableOreFeature}'s configurable equivalent - with a single,
 * config-aware modifier. The unconditional pair meant an ore's {@code enabled = false} could
 * never actually restore vanilla generation: by the time that flag was checked (live, inside
 * {@link ConfigurableOreFeature#place}), vanilla's own feature had already been removed at
 * datapack-load time regardless, so "disabled" only ever meant "nothing generates here",
 * never "vanilla generates here". This checks {@link OreConfig}'s {@code enabled} flag before
 * doing anything at all: enabled swaps vanilla's feature out for the configurable one exactly
 * as before, disabled leaves the biome's generation settings completely untouched, so vanilla
 * ore generation is what actually runs.
 * <p>
 * Like any biome modifier, this only runs once per server start (NeoForge applies all biome
 * modifiers from {@code ServerLifecycleHooks#handleServerAboutToStart}; {@code /reload} does
 * NOT re-run them, since the biome-modifier datapack registry lives in the WORLDGEN layer,
 * which only loads at server start) - so toggling {@code enabled} takes effect on the next
 * world load or server restart, not instantly and not via {@code /reload}. That's an
 * inherent property of the biome modifier system itself (matches how the
 * {@code neoforge:remove_features}/{@code add_features} pair it replaces always behaved
 * too), not something introduced here.
 */
public record ConfigurableOreBiomeModifier(
    OreType ore,
    HolderSet<Biome> biomes,
    HolderSet<PlacedFeature> remove,
    HolderSet<PlacedFeature> add,
    Decoration step
) implements BiomeModifier {
    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (!biomes.contains(biome) || !OreConfig.get(ore).isEnabled()) {
            return;
        }

        BiomeGenerationSettingsBuilder generationSettings = builder.getGenerationSettings();
        if (phase == Phase.REMOVE) {
            generationSettings.getFeatures(step).removeIf(remove::contains);
        } else if (phase == Phase.ADD) {
            add.forEach(holder -> generationSettings.addFeature(step, holder));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return ModBiomeModifiers.CONFIGURABLE_ORE.get();
    }
}
