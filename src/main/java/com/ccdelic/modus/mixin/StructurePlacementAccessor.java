package com.ccdelic.modus.mixin;

import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link StructurePlacement}'s protected {@code frequency()} so
 * {@code ChunkGeneratorMixin} can read it - several structures (e.g. mineshafts, whose
 * placement uses {@code spacing=1, separation=0, frequency=0.004}) use this as their
 * PRIMARY sparsity control rather than spacing, so ignoring it would badly overestimate
 * baseline density and massively over-generate them for any rarity above 1.0.
 */
@Mixin(StructurePlacement.class)
public interface StructurePlacementAccessor {
    @Accessor("frequency")
    float modus$frequency();
}
