/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.energy.block;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pipe.PipeFlowType;

/**
 * Shared "held pipe" interaction for engine blocks.
 *
 * <p>Right-clicking an engine (without crouching) while holding a pipe places that pipe against the
 * engine instead of opening the engine GUI — but only for pipe types that actually connect to that
 * engine. Each engine accepts one flow family in full (the pipe family that feeds its input) plus
 * the wooden extraction variant of another family (the family that draws its output). Any other
 * pipe — and any accepted pipe whose placement is obstructed — falls through to the GUI, so the
 * click never silently does nothing.
 *
 * <p>Crouch-clicking bypasses the block's {@code useItemOn} entirely (see
 * {@code ServerPlayerGameMode#useItemOn}), so the player can still vanilla-place any pipe against an
 * engine by sneaking.
 */
final class EnginePipeInteraction {
    private EnginePipeInteraction() {}

    /**
     * Attempt to place a held pipe against an engine.
     *
     * @param fullFamily           flow type accepted in every variant (the engine's pipe input family)
     * @param extractionOnlyFamily flow type accepted only as a wooden extraction variant (the engine's output family)
     * @return the placement result when an accepted pipe was placed successfully; {@code null} when the
     *         engine should open its GUI instead — either the pipe type is not accepted, or its
     *         placement was obstructed.
     */
    @Nullable
    static InteractionResult tryPlacePipe(IItemPipe pipe, ItemStack stack, Level level, Player player,
            InteractionHand hand, BlockHitResult hitResult,
            PipeFlowType fullFamily, PipeFlowType extractionOnlyFamily) {
        if (!accepts(pipe.getDefinition(), fullFamily, extractionOnlyFamily)) {
            return null;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        // Place it ourselves rather than returning PASS: that way a failed placement (target cell
        // occupied, obstructed, ...) is observable here and can fall back to opening the GUI.
        InteractionResult result = blockItem.place(new BlockPlaceContext(level, player, hand, stack, hitResult));
        return result.consumesAction() ? result : null;
    }

    /** A pipe is accepted if it belongs to the engine's input family (any variant) or is a wooden
     *  extraction variant of the engine's output family. */
    static boolean accepts(PipeDefinition def, PipeFlowType fullFamily, PipeFlowType extractionOnlyFamily) {
        if (def.flowType == fullFamily) {
            return true;
        }
        return def.flowType == extractionOnlyFamily && isExtractionPipe(def);
    }

    /** Wooden pipes — and the emerald (diamond-wood) variant, which shares the same
     *  {@code PipeBehaviourWood}/{@code PipeBehaviourWoodPower} extraction logic — are
     *  BuildCraft's extraction pipes. */
    static boolean isExtractionPipe(PipeDefinition def) {
        String id = def.identifier;
        if (id == null) {
            return false;
        }
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.startsWith("wood_") || path.startsWith("diamond_wood_");
    }
}
