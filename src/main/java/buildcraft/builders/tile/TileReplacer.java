/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.tile;

import java.util.Date;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.enums.EnumSnapshotType;
import buildcraft.api.schematics.ISchematicBlock;

import buildcraft.lib.gui.IBCMenuProvider;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.container.ContainerReplacer;
import buildcraft.builders.item.ItemSchematicSingle;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.Blueprint;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.Snapshot;

public class TileReplacer extends TileBC_Neptune implements IBCMenuProvider {

    /** Accepts a used blueprint item. */
    public final ItemHandlerSimple invSnapshot = itemManager.addInvHandler(
        "snapshot", 1,
        (slot, stack) -> stack.getItem() instanceof ItemSnapshot snap && snap.isUsed()
            && snap.getSnapshotType() == EnumSnapshotType.BLUEPRINT,
        EnumAccess.NONE
    );
    /** Accepts a used schematic-single item (the "from" pattern). */
    public final ItemHandlerSimple invSchematicFrom = itemManager.addInvHandler(
        "schematicFrom", 1,
        (slot, stack) -> stack.getItem() instanceof ItemSchematicSingle s && s.isUsed(),
        EnumAccess.NONE
    );
    /** Accepts a used schematic-single item (the "to" pattern). */
    public final ItemHandlerSimple invSchematicTo = itemManager.addInvHandler(
        "schematicTo", 1,
        (slot, stack) -> stack.getItem() instanceof ItemSchematicSingle s && s.isUsed(),
        EnumAccess.NONE
    );

    public TileReplacer(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.REPLACER.get(), pos, state);
    }

    /**
     * Perform the palette replacement on whatever blueprint is currently in {@link #invSnapshot},
     * using {@link #invSchematicFrom} / {@link #invSchematicTo} as the match/replacement pattern.
     * Server-only; invoked by {@link ContainerReplacer} when the player clicks the Replace button.
     * <p>
     * Unlike the 1.12.2 version this does <b>not</b> consume the single-block schematics — the
     * same from/to pair can be reused on a different blueprint. On success the slot-0 blueprint
     * item is swapped for a new one referencing the newly-built blueprint's {@code Snapshot.Key}.
     *
     * @param newName optional rename for the produced blueprint. {@code null} or blank keeps
     *                the original blueprint's name.
     */
    public void doReplace(@Nullable String newName) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (invSnapshot.getStackInSlot(0).isEmpty()
                || invSchematicFrom.getStackInSlot(0).isEmpty()
                || invSchematicTo.getStackInSlot(0).isEmpty()) {
            return;
        }
        Snapshot.Header header = ItemSnapshot.getHeader(invSnapshot.getStackInSlot(0));
        if (header == null) {
            return;
        }
        Snapshot snapshot = GlobalSavedDataSnapshots.get(level).getSnapshot(header.key);
        if (!(snapshot instanceof Blueprint blueprint)) {
            return;
        }
        try {
            ISchematicBlock from = ItemSchematicSingle.getSchematic(invSchematicFrom.getStackInSlot(0));
            ISchematicBlock to = ItemSchematicSingle.getSchematic(invSchematicTo.getStackInSlot(0));
            if (from == null || to == null) {
                return;
            }
            Blueprint newBlueprint = blueprint.copy();
            newBlueprint.replace(from, to);
            newBlueprint.computeKey();
            GlobalSavedDataSnapshots.get(level).addSnapshot(newBlueprint);

            String resolvedName = (newName != null && !newName.isBlank()) ? newName.trim() : header.name;
            ItemSnapshot usedItem = BCBuildersItems.BLUEPRINT_USED.get();
            Snapshot.Header newHeader = new Snapshot.Header(
                    newBlueprint.key,
                    header.owner,
                    new Date(),
                    resolvedName
            );
            invSnapshot.setStackInSlot(0, usedItem.createUsedStack(newHeader));
            // Intentionally do NOT clear invSchematicFrom / invSchematicTo — the player can
            // reuse the same from/to pair on the next blueprint they drop in.
            setChanged();
        } catch (InvalidInputDataException e) {
            BCLog.logger.warn("[builders.replacer] Invalid replacer blueprint data", e);
        }
    }

    // --- NBT persistence ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        input.read("items", CompoundTag.CODEC).ifPresent(itemManager::deserializeNBT);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.replacer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerReplacer(containerId, playerInv, this);
    }

    /**
     * Retained for backwards compatibility with any stray ticker call sites — no-op now that
     * replacement is triggered on-demand by the Replace button. Safe to remove in a follow-up
     * once the entire call graph is audited.
     */
    @Deprecated
    public void tick() {
        // Intentionally empty: replace() is no longer a tick-time operation.
    }
}
