/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport.wire;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.transport.EnumWirePart;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.IPipeHolder;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.NBTUtilBC;

public class WireManager implements IWireManager {
    private final IPipeHolder holder;
    public final Map<EnumWirePart, DyeColor> parts = new EnumMap<>(EnumWirePart.class);
    public final Set<EnumWirePart> poweredClient = EnumSet.noneOf(EnumWirePart.class);
    public final Map<EnumWireBetween, DyeColor> betweens = new EnumMap<>(EnumWireBetween.class);
    public boolean initialised = false;
    /** Set by {@link #requestBetweensRefresh}; cleared by a non-recursive {@link #updateBetweens}. A recursive pass
     *  (a neighbour recomputing this pipe) leaves it set while {@link #neighboursStale} is pending. */
    private boolean betweensStale = false;
    /** Whether the pending refresh also recomputes the neighbours' links (see {@link #requestBetweensRefresh}). */
    private boolean neighboursStale = false;

    public WireManager(IPipeHolder holder) {
        this.holder = holder;
    }

    public SavedDataWireSystems getWireSystems() {
        return SavedDataWireSystems.get(holder.getPipeWorld());
    }

    @Override
    public IPipeHolder getHolder() {
        return holder;
    }

    /** Recompute {@link #betweens} on the next tick. The links depend on this pipe's connections and its neighbours'
     *  wires and presence, any of which can change without touching this pipe's own wire parts: a neighbour removed
     *  or replaced, a connection made or broken, and on the client a sync arriving after the first tick. Separate
     *  from {@link #initialised}, which on the server would also rebuild the wire systems.
     *  @param neighbours also recompute the neighbours' links: a client sync may carry wires of this pipe that a
     *      neighbour links to, and on the client nothing else tells that neighbour. */
    public void requestBetweensRefresh(boolean neighbours) {
        betweensStale = true;
        neighboursStale |= neighbours;
    }

    public void tick() {
        if (!initialised) {
            initialised = true;
            if (!holder.getPipeWorld().isClientSide()) {
                // The wire tick at the end of this level tick builds each system once, however many of its pipes
                // loaded together.
                getWireSystems().markStaleIfWired(holder.getPipePos(), !parts.isEmpty());
            }
            updateBetweens(false);
        } else if (betweensStale) {
            updateBetweens(!neighboursStale);
        }
    }

    @Override
    public boolean addPart(EnumWirePart part, DyeColor colour) {
        if (getColorOfPart(part) == null) {
            parts.put(part, colour);
            if (!holder.getPipeWorld().isClientSide()) {
                getWireSystems().buildAndAddWireSystem(new WireSystem.WireElement(holder.getPipePos(), part));
                holder.getPipeTile().setChanged();
            }
            updateBetweens(false);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public DyeColor removePart(EnumWirePart part) {
        DyeColor color = getColorOfPart(part);
        if (color == null) {
            return null;
        } else {
            parts.remove(part);
            if (!holder.getPipeWorld().isClientSide()) {
                WireSystem.WireElement element = new WireSystem.WireElement(holder.getPipePos(), part);
                WireSystem.getConnectedElementsOfElement(holder, element)
                    .forEach(getWireSystems()::buildAndAddWireSystem);
                getWireSystems().getWireSystemsWithElement(element).forEach(getWireSystems()::removeWireSystem);
                holder.getPipeTile().setChanged();
            }
            updateBetweens(false);
            return color;
        }
    }

    public void removeParts(Collection<EnumWirePart> toRemove) {
        toRemove.forEach(this.parts::remove);
        if (!holder.getPipeWorld().isClientSide()) {
            removePartsFromSystem(toRemove);
        }
        updateBetweens(false);
    }

    private void removePartsFromSystem(Collection<EnumWirePart> toRemove) {
        toRemove.stream().map(part -> new WireSystem.WireElement(holder.getPipePos(), part))
            .flatMap(element -> WireSystem.getConnectedElementsOfElement(holder, element).stream()).distinct()
            .forEach(getWireSystems()::buildAndAddWireSystem);
        toRemove.stream().map(part -> new WireSystem.WireElement(holder.getPipePos(), part))
            .flatMap(element -> getWireSystems().getWireSystemsWithElement(element).stream())
            .forEach(getWireSystems()::removeWireSystem);
        holder.getPipeTile().setChanged();
    }

    @Override
    public void updateBetweens(boolean recursive) {
        if (!recursive) {
            neighboursStale = false;
        }
        // A neighbour's recursive pass recomputes these links but must not swallow a pending neighbour pass.
        betweensStale = neighboursStale;
        betweens.clear();
        parts.forEach((part, color) -> {
            for (EnumWireBetween between : EnumWireBetween.VALUES) {
                EnumWirePart[] betweenParts = between.parts;
                if (between.to == null) {
                    if ((betweenParts[0] == part && getColorOfPart(betweenParts[1]) == color)
                        || (betweenParts[1] == part && getColorOfPart(betweenParts[0]) == color)) {
                        betweens.put(between, color);
                    }
                } else if (WireSystem.canWireConnect(holder, between.to)) {
                    IPipe pipe = holder.getNeighbourPipe(between.to);
                    if (pipe != null) {
                        IWireManager wireManager = pipe.getHolder().getWireManager();
                        if (betweenParts[0] == part && wireManager.getColorOfPart(betweenParts[1]) == color) {
                            betweens.put(between, color);
                        }
                    }
                }
            }
        });
        holder.onWireLinksChanged();

        if (!recursive) {
            for (Direction side : Direction.values()) {
                BlockPos neighbourPos = holder.getPipePos().relative(side);
                // getBlockEntity would load (or generate) a chunk not in memory on the server; isLoaded is only a
                // ticket test there, so ask for presence.
                if (!BlockUtil.isChunkPresent(holder.getPipeWorld(), neighbourPos)) {
                    continue;
                }
                BlockEntity tile = holder.getPipeWorld().getBlockEntity(neighbourPos);
                if (tile instanceof IPipeHolder) {
                    ((IPipeHolder) tile).getWireManager().updateBetweens(true);
                }
            }
        }
    }

    @Override
    public DyeColor getColorOfPart(EnumWirePart part) {
        return parts.get(part);
    }

    @Override
    public boolean hasPartOfColor(DyeColor color) {
        return parts.values().contains(color);
    }

    @Override
    public boolean isPowered(EnumWirePart part) {
        if (holder.getPipeWorld().isClientSide()) {
            return poweredClient.contains(part);
        } else {
            SavedDataWireSystems wireSystems = this.getWireSystems();
            List<WireSystem> wireSystemsWithElement = wireSystems.getWireSystemsWithElementAsReadOnlyList(new WireSystem.WireElement(holder.getPipePos(), part));
            if (!wireSystemsWithElement.isEmpty()) {
                for (WireSystem wireSystem : wireSystemsWithElement) {
                    Boolean powered = wireSystems.wireSystems.get(wireSystem);
                    if (powered != null && powered) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @Override
    public boolean isAnyPowered(DyeColor color) {
        if (!this.parts.isEmpty()) {
            for (Map.Entry<EnumWirePart, DyeColor> partColor : this.parts.entrySet()) {
                if (partColor.getValue() == color && this.isPowered(partColor.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    public CompoundTag writeToNbt() {
        CompoundTag nbt = new CompoundTag();
        int[] wiresArray = new int[parts.size() * 2];
        int[] i = { 0 };
        parts.forEach((part, color) -> {
            wiresArray[i[0]] = part.ordinal();
            wiresArray[i[0] + 1] = color.getId();
            i[0] += 2;
        });
        nbt.putIntArray("parts", wiresArray);
        return nbt;
    }

    public void readFromNbt(CompoundTag nbt) {
        parts.clear();
        int[] wiresArray = NBTUtilBC.getIntArray(nbt, "parts", new int[0]);
        // Flat (part ordinal, colour id) pairs. A pipe save or a blueprint's tile NBT is not guaranteed well formed, so
        // a pair naming an unknown part or colour is dropped, as is an unpaired trailing element, rather than letting
        // an exception escape the tile's readData.
        for (int i = 0; i + 1 < wiresArray.length; i += 2) {
            int partIndex = wiresArray[i];
            int colourId = wiresArray[i + 1];
            if (partIndex < 0 || partIndex >= EnumWirePart.VALUES.length) {
                continue;
            }
            DyeColor colour = DyeColor.byId(colourId);
            // byId maps an unknown id to white instead of failing, so only an id that reads back as itself is real.
            if (colour.getId() != colourId) {
                continue;
            }
            parts.put(EnumWirePart.VALUES[partIndex], colour);
        }
    }
}
