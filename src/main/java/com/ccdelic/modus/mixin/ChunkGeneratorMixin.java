package com.ccdelic.modus.mixin;

import com.mojang.datafixers.util.Pair;
import com.ccdelic.modus.structures.StructureRarityRegistry;
import com.ccdelic.modus.structures.StructureRaritySeededRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.Predicate;

/**
 * Applies {@link StructureRarityRegistry} to structure generation via two independent
 * mechanisms, deliberately kept separate because they can only ever move density in one
 * direction each:
 * <ul>
 *   <li>{@link #modus$applyStructureRarity} rejects attempts vanilla already decided to
 *   make (always for disabled structures, probabilistically for rarity in [0.1, 1.0)) - a
 *   veto-only tool, so it can only make things rarer.</li>
 *   <li>{@link #modus$extraStructureAttempts} adds independent, extra generation rolls
 *   during normal per-chunk generation for rarity in (1.0, 10.0] - the only way to make
 *   things MORE common.</li>
 * </ul>
 * Both are pure generation-time toggles: they decide whether a structure start gets
 * committed while a chunk is being generated, so - like a datapack change - they only affect
 * chunks generated after the config value took effect. A chunk that generated a now-disabled
 * structure before it was disabled keeps that structure; nothing here retroactively removes
 * it, the same way vanilla itself never retroactively edits already-generated chunks.
 * <p>
 * "More common" was originally attempted by scaling the structure's placement spacing grid
 * directly (shrinking it so more chunks become valid candidates). That approach was
 * abandoned after it crashed the server during testing: vanilla's own structure search
 * ({@code ChunkGenerator#getNearestGeneratedStructure}, which both {@code /locate} and
 * exclusion-zone checks use) reads the placement's spacing to size its search step, and a
 * search calibrated for vanilla's normal (sparse) density ran into vastly more
 * expensive-to-validate candidates once the grid was made significantly denser, blowing
 * past the 60-second watchdog limit on a single {@code /locate structure} call. The
 * extra-roll design below never touches the grid at all, so it can't affect {@code /locate}
 * or anything else that assumes vanilla's spacing is unmodified, and its cost is bounded by
 * however many chunks are actually being generated (a naturally paced process) rather than
 * by an unbounded iterative search.
 * <p>
 * Two more, related crashes were found the same way for the OTHER direction (rarity below
 * 1.0, and disabling): unlike normal per-chunk generation, {@code /locate} (and vanilla's
 * own structure-exclusion checks) walk {@code ChunkGenerator#findNearestMapStructure} /
 * {@code getNearestGeneratedStructure} / {@code getStructureGeneratingAt}, expanding their
 * search radius outward until a candidate succeeds. A disabled structure can never succeed,
 * so nothing bounds that expansion - confirmed by two separate watchdog crashes on a single
 * {@code /locate structure minecraft:village_plains} call after disabling it, from two
 * different bottlenecks depending on what the search happened to spend its time on:
 * <ul>
 *   <li>First crash: {@code StructureCheck#canCreateStructure}'s biome pre-check knows
 *   nothing about this mod's config, so every biome-eligible candidate (village_plains'
 *   biome, plains, is common) still looked promising to it, forcing the same expensive real
 *   generation this class vetoes anyway, over and over. {@code StructureCheckMixin} fixes
 *   this specific cost by making that cheap pre-check agree with this class's veto using the
 *   exact same deterministic roll, so it rejects just as reliably without the expensive real
 *   generation - a genuine improvement, but only to the cost of each rejected candidate, not
 *   to how many candidates an unbounded search can rack up.</li>
 *   <li>Second crash, on a freshly generated world with {@code StructureCheckMixin} already
 *   in place: {@code StructureCheck#tryLoadFromStorage}'s async chunk-region-file scan
 *   (called for every candidate, before either pre-check above even runs) backed up under
 *   the concurrent I/O load of a search issuing far more of these than usual in one tick.
 *   Different bottleneck, same root cause: nothing stops the search from trying an unbounded
 *   number of candidates when the structure is disabled.</li>
 * </ul>
 * The actual fix, since a disabled structure provably can never be found no matter how the
 * per-candidate cost is optimized: {@link #modus$skipSearchForFullyDisabledStructures} skips
 * the search itself - reporting "not found" immediately, before the loop that visits
 * candidates ever runs - when every structure being searched for is disabled. This still
 * doesn't touch the placement grid (same principle as above), and rarity in [0.1, 1.0) is
 * deliberately left to run the (now cheaper, via {@code StructureCheckMixin}) search rather
 * than being skipped outright, since success there is merely less likely, not impossible, so
 * the search is expected to terminate.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true)
    private void modus$skipSearchForFullyDisabledStructures(
        ServerLevel level,
        HolderSet<Structure> structure,
        BlockPos pos,
        int searchRadius,
        boolean skipKnownStructures,
        CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> cir
    ) {
        for (Holder<Structure> holder : structure) {
            ResourceLocation structureId = holder.unwrapKey().map(ResourceKey::location).orElse(null);
            if (structureId == null || StructureRarityRegistry.get(structureId).enabled()) {
                return;
            }
        }
        cir.setReturnValue(null);
    }

    @Shadow
    private boolean tryGenerateStructure(
        StructureSet.StructureSelectionEntry structureSelectionEntry,
        StructureManager structureManager,
        RegistryAccess registryAccess,
        RandomState random,
        StructureTemplateManager structureTemplateManager,
        long seed,
        ChunkAccess chunk,
        ChunkPos chunkPos,
        SectionPos sectionPos
    ) {
        throw new UnsupportedOperationException("mixin shadow");
    }

    @Inject(
        method = "tryGenerateStructure",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/StructureManager;setStartForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;Lnet/minecraft/world/level/levelgen/structure/StructureStart;Lnet/minecraft/world/level/chunk/StructureAccess;)V"
        ),
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void modus$applyStructureRarity(
        StructureSet.StructureSelectionEntry entry,
        StructureManager structureManager,
        RegistryAccess registryAccess,
        RandomState randomState,
        StructureTemplateManager structureTemplateManager,
        long levelSeed,
        ChunkAccess chunkAccess,
        ChunkPos chunkPos,
        SectionPos sectionPos,
        CallbackInfoReturnable<Boolean> cir,
        Structure structure,
        int references,
        HolderSet<Biome> biomes,
        Predicate<Holder<Biome>> validBiome,
        StructureStart structureStart
    ) {
        ResourceLocation structureId = entry.structure().unwrapKey().map(key -> key.location()).orElse(null);
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

        RandomSource random = StructureRaritySeededRandom.create(levelSeed, chunkPos, structureId, StructureRaritySeededRandom.VETO_SALT);
        if (random.nextDouble() >= rarity) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "createStructures", at = @At("RETURN"))
    private void modus$extraStructureAttempts(
        RegistryAccess registryAccess,
        ChunkGeneratorStructureState structureState,
        StructureManager structureManager,
        ChunkAccess chunk,
        StructureTemplateManager structureTemplateManager,
        CallbackInfo ci
    ) {
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.bottomOf(chunk);
        long levelSeed = structureState.getLevelSeed();

        for (Holder<StructureSet> setHolder : structureState.possibleStructureSets()) {
            StructureSet set = setHolder.value();
            StructurePlacement placement = set.placement();
            if (!(placement instanceof RandomSpreadStructurePlacement spreadPlacement)) {
                continue;
            }

            int spacing = spreadPlacement.spacing();
            if (spacing <= 0) {
                continue;
            }

            // Some structures (e.g. mineshafts: spacing=1, separation=0) rely on `frequency`
            // as their real sparsity control rather than spacing. Ignoring it would treat a
            // spacing=1 placement as "already in every chunk" and wildly over-generate it for
            // any rarity above 1.0.
            float frequency = ((StructurePlacementAccessor) placement).modus$frequency();
            double baselineDensityPerChunk = frequency / ((double) spacing * spacing);

            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Structure structure = entry.structure().value();
                ResourceLocation structureId = entry.structure().unwrapKey().map(key -> key.location()).orElse(null);
                if (structureId == null) {
                    continue;
                }

                StructureRarityRegistry.Entry rarityEntry = StructureRarityRegistry.get(structureId);
                if (!rarityEntry.enabled() || rarityEntry.rarity() <= 1.0) {
                    continue;
                }

                StructureStart existing = structureManager.getStartForStructure(sectionPos, structure, chunk);
                if (existing != null && existing.isValid()) {
                    continue;
                }

                double extraChance = Math.min(1.0, (rarityEntry.rarity() - 1.0) * baselineDensityPerChunk);
                RandomSource random = StructureRaritySeededRandom.create(
                    levelSeed, chunkPos, structureId, StructureRaritySeededRandom.EXTRA_ATTEMPT_SALT
                );
                double roll = random.nextDouble();
                if (roll < extraChance) {
                    this.tryGenerateStructure(
                        entry, structureManager, registryAccess, structureState.randomState(), structureTemplateManager,
                        levelSeed, chunk, chunkPos, sectionPos
                    );
                }
            }
        }
    }
}
