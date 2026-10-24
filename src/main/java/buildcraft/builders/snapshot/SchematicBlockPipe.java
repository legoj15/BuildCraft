/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
 * from NBT, so they would otherwise be placed for free. A pluggable it cannot cost is not placed
 * either: the NBT written into the built pipe is the captured one minus every pluggable that did not
 * price, so what is charged, what is written and what ends up in the world are the same set. The
 * stored blueprint is never edited. Everything else — block-state capture, NBT capture, placement,
 * {@code isBuilt} — is inherited unchanged.
 *
 * <p>Registered just above {@code default} (and below {@code fluid}, which a pipe never matches even when it
 * is waterlogged: the holder is a {@code SimpleWaterloggedBlock}, but the fluid schematic types only a fluid's own
 * block, so a wet pipe is still a pipe); see {@code BCBuildersSchematics}.
 */
public class SchematicBlockPipe extends SchematicBlockDefault {
    private static final Logger LOGGER = LogManager.getLogger("BCBlueprint");

    @SuppressWarnings("unused")
    public static boolean predicate(SchematicBlockContext context) {
        return context.block instanceof BlockPipeHolder
            && SchematicBlockDefault.predicate(context);
    }

    @Nonnull
    @Override
    public List<ItemStack> computeRequiredItems(boolean includeContainerContents) {
        ItemStack pipeStack = resolvePipeItem();
        // The pipe item (or, for a definition missing / an item unmapped, e.g. a pipe from a removed add-on, the
        // generic block-item path rather than silently requiring nothing), plus any pluggables (gates / facades /
        // plugs / lenses) attached to it. build() writes the pluggables the captured BE NBT carries, so they are
        // charged for either way: the holder is placed and its plugs load whether or not the pipe resolves.
        List<ItemStack> required = new ArrayList<>();
        if (pipeStack == null || pipeStack.isEmpty()) {
            required.addAll(super.computeRequiredItems(includeContainerContents));
        } else {
            required.add(pipeStack);
        }
        for (ItemStack pluggable : pricedPluggables(tileNbt).values()) {
            required.add(pluggable);
        }
        return required;
    }

    /**
     * Writes the pipe's tile data without the pluggables the blueprint could not price. {@code data} is already the
     * disposable copy both build paths hand over; the stored {@link #tileNbt} is never touched.
     */
    @Override
    protected void loadTileData(Level level, BlockPos blockPos, CompoundTag data) {
        super.loadTileData(level, blockPos, withoutUnpriceablePluggables(data));
    }

    /**
     * What each captured pluggable (gate / facade / plug / lens / …) is priced at, by the face it is mounted on.
     * Pluggables live under the {@code plugs} BE tag, one entry per face, and {@code build()} restores them from that
     * NBT; a face is in the result only when its entry can be priced, and that is the only thing that lets the
     * pluggable be placed ({@link #withoutUnpriceablePluggables}).
     *
     * <p>Each is reconstructed with a {@code null} holder purely to call its
     * {@link PipePluggable#getPickStack()}: every getPickStack reads only the pluggable's own parsed
     * state (facade states, gate variant, lens colour), never the holder, and construction stores the
     * holder without dereferencing it (event-bus registration is done separately by the tile, not the
     * constructor). An entry that names no registered definition, fails to rebuild holder-free (a holder-dependent
     * reconstruction, an unknown variant, damaged data), rebuilds to nothing or has no pick stack has no price, so
     * it is not in the result. Never a crash. {@code SchematicBlockPipePluggablePricingTester} checks that every
     * pluggable BuildCraft registers does price, so none of its own is ever left off a blueprint.
     */
    @Nonnull
    private static Map<Direction, ItemStack> pricedPluggables(@Nullable CompoundTag tileNbt) {
        Map<Direction, ItemStack> priced = new EnumMap<>(Direction.class);
        if (tileNbt == null || PipeApi.pluggableRegistry == null) {
            return priced;
        }
        CompoundTag plugTag = NBTUtilBC.getCompound(tileNbt, "plugs");
        if (plugTag.isEmpty()) {
            return priced;
        }
        for (Direction face : Direction.values()) {
            CompoundTag entry = NBTUtilBC.getCompound(plugTag, face.getName());
            String plugId = NBTUtilBC.getString(entry, "id", "");
            if (plugId.isEmpty()) {
                continue;
            }
            try {
                PluggableDefinition def = PipeApi.pluggableRegistry.getDefinition(Identifier.parse(plugId));
                if (def == null) {
                    continue;
                }
                // A copy: a pluggable migrates a legacy layout in place while it reads, and this runs on the stored data.
                PipePluggable plug = def.readFromNbt(null, face, NBTUtilBC.getCompound(entry, "data").copy());
                if (plug != null) {
                    ItemStack stack = plug.getPickStack();
                    if (stack != null && !stack.isEmpty()) {
                        priced.put(face, stack);
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                // A malformed id, or a reconstruction that needs a holder or rejects its data: no price. The id comes
                // from blueprint bytes, and the exception message may echo it, so neither goes to the log raw.
                LOGGER.debug("Cannot price the pluggable {} on the {} face of a captured pipe ({})",
                    Snapshot.logSafe(plugId, 256), face, Snapshot.logSafe(e, 256));
            }
        }
        return priced;
    }

    /**
     * {@code tileNbt} as the build should write it: the same pipe, with every pluggable that
     * {@link #pricedPluggables} could not price taken off its {@code plugs} tag. The pluggables left are exactly the
     * ones {@link #computeRequiredItems} charges for, so nothing is placed for free, and a pluggable whose data cannot
     * be loaded can no longer take the whole pipe down with it (the loader gives up on the block entity and replaces it
     * with an empty holder). Returns {@code tileNbt} itself when nothing is removed, otherwise an edited copy, so the
     * blueprint's own data keeps what it was captured with (an add-on installed again later finds its pluggables).
     */
    @Nonnull
    static CompoundTag withoutUnpriceablePluggables(@Nonnull CompoundTag tileNbt) {
        CompoundTag plugTag = NBTUtilBC.getCompound(tileNbt, "plugs");
        if (plugTag.isEmpty()) {
            return tileNbt;
        }
        Map<Direction, ItemStack> priced = pricedPluggables(tileNbt);
        CompoundTag kept = new CompoundTag();
        boolean removed = false;
        for (Direction face : Direction.values()) {
            CompoundTag entry = NBTUtilBC.getCompound(plugTag, face.getName());
            if (entry.isEmpty()) {
                continue;
            }
            if (priced.containsKey(face)) {
                kept.put(face.getName(), entry);
            } else {
                removed = true;
                // Debug, not warn: a blueprint from a removed add-on repeats this for every pipe that carries one.
                LOGGER.debug("Leaving the {} pluggable on the {} face off a pipe being built: it cannot be priced",
                    Snapshot.logSafe(NBTUtilBC.getString(entry, "id", ""), 256), face);
            }
        }
        if (!removed) {
            return tileNbt;
        }
        CompoundTag copy = tileNbt.copy();
        if (kept.isEmpty()) {
            copy.remove("plugs");
        } else {
            copy.put("plugs", kept.copy());
        }
        return copy;
    }

    @Override
    public SchematicBlockDefault getRotated(Rotation rotation) {
        SchematicBlockDefault rotated = super.getRotated(rotation);
        // super hands the rotated copy its own copy of tileNbt. Pipes additionally need their face-indexed
        // pluggables rotated, so a facade captured on (say) NORTH lands on the face matching the
        // rotated blueprint instead of staying NORTH — where it would block the wrong pipe
        // connection and cut off flow. Pipe connections themselves recompute on placement, so only
        // the pluggable mounting faces need remapping here. Rotating the copy (never this.tileNbt) keeps
        // the unrotated schematic's data out of the rotated one's reach, rotation NONE included.
        if (rotated instanceof SchematicBlockPipe pipe && pipe.tileNbt != null) {
            pipe.tileNbt = rotatePluggableFaces(pipe.tileNbt, rotation);
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
