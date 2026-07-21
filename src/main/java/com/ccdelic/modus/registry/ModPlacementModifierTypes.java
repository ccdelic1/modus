package com.ccdelic.modus.registry;

import com.ccdelic.modus.Modus;
import com.ccdelic.modus.worldgen.ConfigurableCountPlacement;
import com.ccdelic.modus.worldgen.ConfigurableHeightRangePlacement;
import com.ccdelic.modus.worldgen.ConfigurableRarityFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModPlacementModifierTypes {
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIER_TYPES = DeferredRegister.create(
        Registries.PLACEMENT_MODIFIER_TYPE, Modus.MODID
    );

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigurableCountPlacement>> CONFIGURABLE_COUNT =
        PLACEMENT_MODIFIER_TYPES.register("configurable_count", () -> () -> ConfigurableCountPlacement.CODEC);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigurableRarityFilter>> CONFIGURABLE_RARITY_FILTER =
        PLACEMENT_MODIFIER_TYPES.register("configurable_rarity_filter", () -> () -> ConfigurableRarityFilter.CODEC);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigurableHeightRangePlacement>> CONFIGURABLE_HEIGHT_RANGE =
        PLACEMENT_MODIFIER_TYPES.register("configurable_height_range", () -> () -> ConfigurableHeightRangePlacement.CODEC);

    private ModPlacementModifierTypes() {
    }
}
