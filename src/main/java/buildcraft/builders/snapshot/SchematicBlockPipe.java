/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.api.transport.pipe.IItemPipe;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.lib.misc.NBTUtilBC;

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
 * requirement display and item consumption line up with what actually gets built. It also costs any
 * pluggables (gates, facades, plugs, lenses) captured on the pipe — {@code build()} restores those
 * from NBT, so they would otherwise be placed for free. Everything else — block-state capture, NBT
 * capture, placement, {@code isBuilt} — is inherited unchanged.
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
        if (pipeStack == null || pipeStack.isEmpty()) {
            // Definition missing / item unmapped (e.g. a pipe from a removed add-on): fall back to the
            // generic block-item path rather than silently requiring nothing. Any pluggables on an
            // unresolvable pipe are moot — the pipe itself wouldn't build.
            return super.computeRequiredItems(includeContainerContents);
        }
        // The pipe item, plus any pluggables (gates / facades / plugs / lenses) attached to it.
        // build() restores pluggables from the captured BE NBT, so without listing them here the
        // Builder would place them for free.
        List<ItemStack> required = new ArrayList<>();
        required.add(pipeStack);
        addPluggableItems(required);
        return required;
    }

    /**
     * Append the item for every pluggable (gate / facade / plug / lens / …) captured on the pipe.
     * Pluggables live under the {@code plugs} BE tag, one entry per face, and {@code build()} restores
     * them verbatim from that NBT — so they must be costed here or the Builder places them for free.
     *
     * <p>Each is reconstructed with a {@code null} holder purely to call its
     * {@link PipePluggable#getPickStack()}: every getPickStack reads only the pluggable's own parsed
     * state (facade states, gate variant, lens colour), never the holder, and construction stores the
     * holder without dereferencing it (event-bus registration is done separately by the tile, not the
     * constructor). The try/catch leaves any pluggable that can't be rebuilt holder-free simply
     * uncosted — never a crash, and no worse than before.
     */
    private void addPluggableItems(@Nonnull List<ItemStack> out) {
        if (tileNbt == null || PipeApi.pluggableRegistry == null) {
            return;
        }
        CompoundTag plugTag = NBTUtilBC.getCompound(tileNbt, "plugs");
        if (plugTag.isEmpty()) {
            return;
        }
        for (Direction face : Direction.values()) {
            CompoundTag entry = NBTUtilBC.getCompound(plugTag, face.getName());
            String plugId = NBTUtilBC.getString(entry, "id", "");
            if (plugId.isEmpty()) {
                continue;
            }
            PluggableDefinition def = PipeApi.pluggableRegistry.getDefinition(Identifier.parse(plugId));
            if (def == null) {
                continue;
            }
            try {
                PipePluggable plug = def.readFromNbt(null, face, NBTUtilBC.getCompound(entry, "data"));
                if (plug != null) {
                    ItemStack stack = plug.getPickStack();
                    if (stack != null && !stack.isEmpty()) {
                        out.add(stack);
                    }
                }
            } catch (Throwable t) {
                // Holder-dependent reconstruction or unknown variant: leave that pluggable uncosted.
            }
        }
    }

    /**
     * Resolve the base pipe item from the captured {@code pipe.def} NBT. Returns {@code null} if the
     * tile NBT is absent, carries no pipe sub-tag, or names a definition with no registered item.
     * The pipe's paint colour is intentionally NOT copied onto the requirement: the Builder consumes
     * a plain pipe from inventory and {@link SchematicBlockDefault#build} restores the colour from
     * the captured BE NBT after placement, so requiring a pre-painted pipe would only stall the build.
     */
    @Override
    public SchematicBlockDefault getRotated(Rotation rotation) {
        SchematicBlockDefault rotated = super.getRotated(rotation);
        // super shares this.tileNbt by reference. Pipes additionally need their face-indexed
        // pluggables rotated, so a facade captured on (say) NORTH lands on the face matching the
        // rotated blueprint instead of staying NORTH — where it would block the wrong pipe
        // connection and cut off flow. Pipe connections themselves recompute on placement, so only
        // the pluggable mounting faces need remapping here.
        if (rotated instanceof SchematicBlockPipe pipe && tileNbt != null) {
            pipe.tileNbt = rotatePluggableFaces(tileNbt, rotation);
        }
        return rotated;
    }

    /**
     * Return a copy of the pipe tile NBT with the {@code plugs} entries remapped onto rotated faces.
     * UP/DOWN are unaffected (rotation is about the Y axis). The facade's mimicked block state is
     * intentionally left unrotated: a directional facade (stairs, logs) keeps its captured
     * orientation — only its mounting face moves, which is what the flow-blocking issue needs.
     */
    @Nonnull
    private static CompoundTag rotatePluggableFaces(@Nonnull CompoundTag original, Rotation rotation) {
        if (rotation == Rotation.NONE || !original.contains("plugs")) {
            return original;
        }
        CompoundTag copy = original.copy();
        CompoundTag oldPlugs = NBTUtilBC.getCompound(copy, "plugs");
        CompoundTag newPlugs = new CompoundTag();
        for (Direction face : Direction.values()) {
            CompoundTag entry = NBTUtilBC.getCompound(oldPlugs, face.getName());
            if (!entry.isEmpty()) {
                newPlugs.put(rotation.rotate(face).getName(), entry);
            }
        }
        copy.put("plugs", newPlugs);
        return copy;
    }

    @Nullable
    private ItemStack resolvePipeItem() {
        if (tileNbt == null) {
            return null;
        }
        CompoundTag pipeTag = NBTUtilBC.getCompound(tileNbt, "pipe");
        String defId = NBTUtilBC.getString(pipeTag, "def", "");
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
