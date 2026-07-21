package com.ccdelic.modus.mixin;

import com.ccdelic.modus.structures.StructureBlacklistConfig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Extends {@link StructureBlacklistConfig}'s block replacement to structures whose pieces
 * are hand-coded directly in Java instead of built from NBT templates - the four structures
 * {@code StructureBlacklistMixin}'s javadoc documents as out of reach for it (mineshafts,
 * strongholds, ocean monuments, nether fortresses) place their blocks by calling {@code
 * StructurePiece#placeBlock} directly rather than through {@code StructureTemplate
 * #processBlockInfos}.
 * <p>
 * {@code placeBlock} is itself a single shared choke point on the common {@code
 * StructurePiece} base class, so hooking it here reuses the exact same {@link
 * StructureBlacklistConfig#replacementFor} lookup and gets all four "for free" - but the
 * coverage this actually achieves for each of them depends entirely on whether that
 * structure's own code routes its blocks through {@code placeBlock} or writes directly via
 * {@code level.setBlock(...)}, bypassing it. Checked directly against each structure's
 * source rather than assumed:
 * <ul>
 *   <li>{@code minecraft:stronghold} ({@code StrongholdPieces}) and
 *   {@code minecraft:ocean_monument} ({@code OceanMonumentPieces}) - full coverage, both
 *   place effectively all of their blocks through {@code placeBlock}.</li>
 *   <li>{@code minecraft:fortress} ({@code NetherFortressPieces}) - mostly covered (dozens
 *   of {@code placeBlock} calls), but at least its spawner placement calls
 *   {@code level.setBlock} directly and is not reachable.</li>
 *   <li>{@code minecraft:mineshaft} ({@code MineshaftPieces}) - only minimally covered: the
 *   bulk of its block placement (planks, rails, cobwebs, fences, its spawner) calls
 *   {@code level.setBlock} directly, with only two call sites going through
 *   {@code placeBlock}. Hooking every individual direct call site across all four structures
 *   would multiply the number of injection points (and the maintenance burden of keeping
 *   them matched to vanilla's exact method bodies) for content that's overwhelmingly
 *   structural blocks rather than the kind of decorative dressing this feature targets, so
 *   that wasn't attempted.</li>
 * </ul>
 * Replacement blocks use their default state with no block entity data, for the same reason
 * documented on {@link StructureBlacklistConfig} and {@code StructureBlacklistMixin}.
 */
@Mixin(StructurePiece.class)
public abstract class StructurePieceBlacklistMixin {
    @ModifyVariable(method = "placeBlock", at = @At("HEAD"), argsOnly = true)
    private BlockState modus$applyBlockBlacklist(BlockState blockstate) {
        Block replacement = StructureBlacklistConfig.replacementFor(blockstate.getBlock());
        return replacement != null ? replacement.defaultBlockState() : blockstate;
    }
}
