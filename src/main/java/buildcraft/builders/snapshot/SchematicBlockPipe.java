/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeDefinition;

import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.pipe.PipeRegistry;

/**
 * Pipe-aware schematic. BuildCraft pipes are all one block (the pipe holder); the pipe TYPE
 * (wood / cobble / iron / diamond / gold / …) lives in the block-entity NBT, not the block state.
 * {@link SchematicBlockDefault} resolves a placement's required item via
 * {@code blockState.getBlock().asItem()}, which returns the SAME item for every pipe — so a
 * blueprint full of varied pipes lists a single pipe type in the Builder's resource panel even
 * though each pipe still <em>places</em> correctly (placement reads the captured BE NBT). This
 * subclass reads the pipe definition out of that NBT and returns the matching pipe item, so the
 * requirement display and item consumption line up with what actually gets built. Everything else
 * — block-state capture, NBT capture, placement, {@code isBuilt} — is inherited unchanged.
 *
 * <p>Registered just above {@code default} (and below {@code fluid}, which pipes never match —
 * the holder block is not waterloggable); see {@code BCBuildersSchematics}.
 */
public class SchematicBlockPipe extends SchematicBlockDefault {

    @SuppressWarnings("unused")
    public static boolean predicate(SchematicBlockContext context) {
        return context.block instanceof BlockPipeHolder
            && SchematicBlockDefault.predicate(context);
    }

    @Nonnull
    @Override
    public List<ItemStack> computeRequiredItems(boolean includeContainerContents) {
        ItemStack pipeStack = resolvePipeItem();
        if (pipeStack != null && !pipeStack.isEmpty()) {
            return Collections.singletonList(pipeStack);
        }
        // Definition missing / item unmapped (e.g. a pipe from a removed add-on): fall back to the
        // generic block-item path rather than silently requiring nothing.
        return super.computeRequiredItems(includeContainerContents);
    }

    /**
     * Resolve the base pipe item from the captured {@code pipe.def} NBT. Returns {@code null} if the
     * tile NBT is absent, carries no pipe sub-tag, or names a definition with no registered item.
     * The pipe's paint colour is intentionally NOT copied onto the requirement: the Builder consumes
     * a plain pipe from inventory and {@link SchematicBlockDefault#build} restores the colour from
     * the captured BE NBT after placement, so requiring a pre-painted pipe would only stall the build.
     */
    @Nullable
    private ItemStack resolvePipeItem() {
        if (tileNbt == null) {
            return null;
        }
        CompoundTag pipeTag = tileNbt.getCompoundOrEmpty("pipe");
        String defId = pipeTag.getStringOr("def", "");
        if (defId.isEmpty()) {
            return null;
        }
        PipeDefinition def = PipeRegistry.INSTANCE.getDefinition(defId);
        if (def == null) {
            return null;
        }
        IItemPipe itemPipe = PipeRegistry.INSTANCE.getItemForPipe(def);
        if (!(itemPipe instanceof Item item)) {
            return null;
        }
        return new ItemStack(item);
    }
}
