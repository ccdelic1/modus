package com.ccdelic.modus.mixin;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link StructurePlacement}'s protected {@code frequency()} and {@code
 * exclusionZone()} so {@code ChunkGeneratorMixin} can read them.
 * <p>
 * {@code frequency()}: several structures (e.g. mineshafts, whose placement uses
 * {@code spacing=1, separation=0, frequency=0.004}) use this as their PRIMARY sparsity
 * control rather than spacing, so ignoring it would badly overestimate baseline density
 * and massively over-generate them for any rarity above 1.0.
 * <p>
 * {@code exclusionZone()}: an "extra attempt" (rarity {@literal >} 1.0) calls
 * {@code tryGenerateStructure} directly, outside vanilla's own {@code
 * StructurePlacement#isStructureChunk} gate - which normally also rejects a chunk that
 * falls inside another structure set's exclusion zone (e.g. keeping two mutually-exclusive
 * structures from generating too close together). Without re-checking this specific piece
 * of that gate, an extra attempt could place a structure vanilla's own fencing would never
 * have allowed there. This accessor lets {@code ChunkGeneratorMixin} re-check exactly that
 * one piece - not the sparse spacing-grid check, which would defeat the entire point of an
 * "extra", off-grid attempt.
 */
@Mixin(StructurePlacement.class)
public interface StructurePlacementAccessor {
    @Accessor("frequency")
    float modus$frequency();

    @Accessor("exclusionZone")
    Optional<StructurePlacement.ExclusionZone> modus$exclusionZone();
}
