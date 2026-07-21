package com.ccdelic.modus.mixin;

import com.ccdelic.modus.structures.StructureBlacklistConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies {@link StructureBlacklistConfig}'s block replacements right before a structure's
 * blocks are actually placed into the world.
 * <p>
 * {@code StructureTemplate#processBlockInfos} is the single choke point every NBT-template
 * and jigsaw structure placement passes through - {@code SinglePoolElement} (villages,
 * bastions, ancient city, pillager outposts, trail ruins) and {@code TemplateStructurePiece}
 * (igloos, shipwrecks, ocean ruins, desert/jungle temples, end city, ruined portals) both
 * call it, and it hands back the exact, final list of blocks {@code StructureTemplate
 * #placeInWorld} is about to place - post every vanilla and modded
 * {@code StructureProcessor} already in the structure's own settings. Injecting at its
 * return, after all of that has already happened, means this always sees (and can rewrite)
 * the actual final outcome no matter what produced it, without needing to know anything
 * about jigsaw pools, piece types, or any individual structure's own processor list.
 * <p>
 * This is one of THREE block-placement paths a structure can use, each with its own mixin:
 * <ul>
 *   <li>NBT templates - here.</li>
 *   <li>Fully hand-coded piece placement (mineshafts, strongholds, ocean monuments, nether
 *   fortresses place blocks via {@code StructurePiece.placeBlock}, never this method) - see
 *   {@code StructurePieceBlacklistMixin}. The woodland mansion is NOT one of these despite
 *   its hand-coded room layout ({@code WoodlandMansionPieces.WoodlandMansionPiece} still
 *   extends {@code TemplateStructurePiece}, so its room content - bookshelves and all - is
 *   reachable here) - confirmed directly by placing one during testing.</li>
 *   <li>Decoration FEATURES run by a jigsaw {@code feature_pool_element} - e.g. a village's
 *   {@code block_pile} hay/pumpkin/snow piles, which run a feature that calls
 *   {@code level.setBlock} directly, hitting neither this method nor {@code placeBlock}. See
 *   {@code FeaturePoolElementBlacklistMixin} + {@code WorldGenRegionBlacklistMixin}. This was
 *   the subtle one: a village's TEMPLATE hay (baked into stable/animal-pen .nbt files) is
 *   caught here, but its decorative hay PILES were a feature and slipped through until that
 *   third hook was added - confirmed by finding leftover feature-pile hay in an otherwise
 *   correctly-replaced village.</li>
 * </ul>
 * <p>
 * Replacement blocks are placed with their default state and no block entity data - see
 * {@link StructureBlacklistConfig} for why: the replacement's default state is the only
 * state that's guaranteed to be valid for it (the original block's own state, e.g. a log's
 * axis or a stair's facing, has no reason to still make sense on an unrelated block), and
 * carrying over the original's block entity NBT (e.g. a chest's loot table) onto a
 * potentially unrelated block risks loading data the new block doesn't know how to use.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureBlacklistMixin {
    @Inject(
        method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;"
            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void modus$applyBlockBlacklist(
        ServerLevelAccessor serverLevel,
        BlockPos offset,
        BlockPos pos,
        StructurePlaceSettings settings,
        List<StructureTemplate.StructureBlockInfo> blockInfos,
        StructureTemplate template,
        CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> cir
    ) {
        List<StructureTemplate.StructureBlockInfo> original = cir.getReturnValue();
        if (original == null) {
            // Not expected from vanilla, but a modded StructureProcessor#finalizeProcessing
            // could misbehave and return null instead of an empty list - guard rather than
            // let that NPE and take down the whole structure placement.
            return;
        }
        List<StructureTemplate.StructureBlockInfo> replaced = null;

        for (int i = 0; i < original.size(); i++) {
            StructureTemplate.StructureBlockInfo info = original.get(i);
            Block replacement = StructureBlacklistConfig.replacementFor(info.state().getBlock());
            if (replacement != null) {
                if (replaced == null) {
                    replaced = new ArrayList<>(original);
                }
                replaced.set(i, new StructureTemplate.StructureBlockInfo(info.pos(), replacement.defaultBlockState(), null));
            }
        }

        if (replaced != null) {
            cir.setReturnValue(replaced);
        }
    }
}
