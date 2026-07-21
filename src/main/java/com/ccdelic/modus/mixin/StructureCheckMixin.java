package com.ccdelic.modus.mixin;

import com.ccdelic.modus.structures.StructureRarityRegistry;
import com.ccdelic.modus.structures.StructureRaritySeededRandom;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes searches like {@code /locate structure} (and vanilla's own exclusion-zone checks
 * against nearby structures) cheaply agree with {@code ChunkGeneratorMixin}'s veto instead
 * of paying to find out the hard way.
 * <p>
 * {@code StructureCheck#checkStart} only reaches real, expensive chunk generation
 * ({@code ChunkGenerator#getStructureGeneratingAt} calling {@code level.getChunk(...,
 * STRUCTURE_STARTS)}) after its own cheap {@link #modus$vetoDisabledOrRareStructure biome
 * pre-check} ({@code canCreateStructure}) says a candidate chunk could plausibly have the
 * structure. Without this mixin, a disabled structure's biome pre-check still says "maybe"
 * for every biome-eligible candidate (it knows nothing about our config), forcing a real,
 * expensive generation for each one - which {@code ChunkGeneratorMixin} then always vetoes
 * anyway. For a structure placed in a common biome (confirmed with {@code village_plains|
 * plains}) this makes every candidate in the search radius expensive and, because a fully
 * disabled structure can never succeed, gives the search no way to stop early - confirmed by
 * an actual watchdog crash on a single {@code /locate structure} call after disabling it.
 * <p>
 * The fix: reject here too, using the exact same deterministic roll
 * ({@link StructureRaritySeededRandom}) {@code ChunkGeneratorMixin} uses for the real veto,
 * so this cheap pre-check and that expensive real veto always agree - a candidate this
 * predicts will be vetoed is always the one that veto goes on to reject, so nothing this
 * skips could ever have generated anyway.
 */
@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {
    @Shadow
    @Final
    private RegistryAccess registryAccess;

    @Shadow
    @Final
    private long seed;

    @Inject(method = "canCreateStructure", at = @At("HEAD"), cancellable = true)
    private void modus$vetoDisabledOrRareStructure(ChunkPos chunkPos, Structure structure, CallbackInfoReturnable<Boolean> cir) {
        ResourceLocation structureId = this.registryAccess.registryOrThrow(Registries.STRUCTURE).getKey(structure);
        if (structureId == null) {
            return;
        }

        StructureRarityRegistry.Entry rarityEntry = StructureRarityRegistry.get(structureId);
        if (!rarityEntry.enabled()) {
            cir.setReturnValue(false);
            return;
        }

        double rarity = rarityEntry.rarity();
        if (rarity >= 1.0) {
            return;
        }

        RandomSource random = StructureRaritySeededRandom.create(this.seed, chunkPos, structureId, StructureRaritySeededRandom.VETO_SALT);
        if (random.nextDouble() >= rarity) {
            cir.setReturnValue(false);
        }
    }
}
