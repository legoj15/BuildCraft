/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.container;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import buildcraft.lib.gui.slot.SlotBase;

import buildcraft.api.core.BCLog;
import buildcraft.api.filler.IFillerPattern;
import buildcraft.api.tiles.IControllable;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.gui.WideContainerData;
import buildcraft.lib.statement.FullStatement;
import buildcraft.lib.statement.StatementContext;

import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.BCBuildersStatements;
import buildcraft.builders.filler.FillerType;
import buildcraft.builders.tile.TileFiller;
import buildcraft.platform.network.PacketContext;

@SuppressWarnings("this-escape")
public class ContainerFiller extends ContainerBCTile<TileFiller> implements IContainerFilling {

    private final WideContainerData.BooleanValue canExcavate;
    private final WideContainerData.BooleanValue inverted;
    private final WideContainerData.BooleanValue finished;
    private final WideContainerData.BooleanValue locked;
    private final WideContainerData.IntValue mode;
    private final WideContainerData.IntValue toPlace;
    private final WideContainerData.IntValue toBreak;
    private final WideContainerData.BooleanValue preview;
    private final FullStatement<IFillerPattern> patternStatementClient = new FullStatement<>(
        FillerType.INSTANCE,
        4,
        (statement, paramIndex) -> onStatementChange()
    );

    /** The pattern popup's groups, one per {@link BCBuildersStatements#PATTERN_POPUP} row. */
    private static final List<StatementContext.StatementGroup<IFillerPattern>> PATTERN_GROUPS =
        BCBuildersStatements.PATTERN_POPUP.stream()
            .<StatementContext.StatementGroup<IFillerPattern>>map(row -> new StatementContext.StatementGroup<>() {
                private final List<IFillerPattern> values = List.copyOf(row);

                @Override
                public List<IFillerPattern> getValues() {
                    return values;
                }

                @Override
                public ISimpleDrawable getSourceIcon() {
                    return null;
                }
            })
            .toList();

    public final StatementContext<IFillerPattern> possiblePatternsContext = () -> PATTERN_GROUPS;

    public void onStatementChange() {
        if (player != null && player.level() != null && player.level().isClientSide()) {
            sendMessage(NET_STATEMENT, (buf) -> {
                buildcraft.lib.net.PacketBufferBC buffer = new buildcraft.lib.net.PacketBufferBC(buf.unwrap());
                patternStatementClient.writeToBuffer(buffer);
            });
        }
    }

    // Client-side constructor (from network)
    public ContainerFiller(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, resolveTile(playerInv, buf, TileFiller.class));
    }

    // Server-side constructor
    public ContainerFiller(int containerId, Inventory playerInv, TileFiller tile) {
        super(BCBuildersMenuTypes.FILLER.get(), containerId, playerInv.player, tile);

        // WideContainerData keeps the block counts intact through vanilla's 16-bit menu-data packet (Fabric's only one).
        WideContainerData.Builder sync = WideContainerData.builder();
        canExcavate = sync.addBoolean(() -> tile.getCanExcavate());
        inverted = sync.addBoolean(() -> tile.inverted);
        finished = sync.addBoolean(() -> tile.getFinished());
        locked = sync.addBoolean(() -> tile.getLockedTicks() > 0);
        mode = sync.addInt(() -> tile.getModeOrdinal());
        toPlace = sync.addInt(() -> tile.getCountToPlace());
        toBreak = sync.addInt(() -> tile.getCountToBreak());
        preview = sync.addBoolean(() -> tile.isPreviewEnabled());
        addDataSlots(sync.buildFor(tile));

        // 27 resource slots in a 3x9 grid
        if (tile != null) {
            for (int sy = 0; sy < 3; sy++) {
                for (int sx = 0; sx < 9; sx++) {
                    addSlot(new SlotBase(tile.invResources, sx + sy * 9, 8 + sx * 18, 85 + sy * 18));
                }
            }
        }

        // Player inventory at y=153 (aligned to the GUI texture's slot pockets)
        addFullPlayerInventory(8, 153, playerInv);
    }

    // IContainerFilling

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public FullStatement<IFillerPattern> getPatternStatementClient() {
        return patternStatementClient;
    }

    @Override
    public FullStatement<IFillerPattern> getPatternStatement() {
        return tile.patternStatement;
    }

    @Override
    public boolean isInverted() {
        return inverted.get();
    }

    public static final int NET_EXCAVATE = 10;
    public static final int NET_STATEMENT = 11;
    public static final int NET_INVERT = 12;
    public static final int NET_PREVIEW = 13;

    @Override
    public void readMessage(int id, buildcraft.lib.net.PacketBufferBC buffer, boolean isClient, PacketContext ctx) {
        if (id == NET_STATEMENT) {
            try {
                if (isClient) {
                    patternStatementClient.readFromBuffer(buffer);
                } else if (tile != null) {
                    if (tile.isLocked()) {
                        valuesChanged(); // Sync correct state back to client
                        return;
                    }
                    buildcraft.lib.statement.FullStatement<IFillerPattern> stat = getPatternStatement();
                    if (stat != null) {
                        stat.readFromBuffer(buffer);
                        valuesChanged();
                        tile.setChanged(); // Make sure chunk knows to save!
                    }
                }
            } catch (java.io.IOException e) {
                BCLog.logger.warn("[builders.filler] Failed to read filler data from the network buffer", e);
            }
            return;
        }

        super.readMessage(id, buffer, isClient, ctx);
        if (isClient) return;

        if (id == NET_EXCAVATE) {
            if (tile != null) {
                tile.setCanExcavate(!tile.getCanExcavate());
                tile.setChanged();
                valuesChanged();
            }
        } else if (id == NET_INVERT) {
            if (tile != null) {
                tile.inverted = !tile.inverted;
                tile.setChanged();
                valuesChanged();
            }
        } else if (id == NET_PREVIEW) {
            // Not valuesChanged(): showing the preview must not reset a build in progress.
            if (tile != null) {
                tile.setPreviewEnabled(!tile.isPreviewEnabled());
            }
        }
    }

    @Override
    public void valuesChanged() {
        if (tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            tile.onStatementChange();
        }
    }

    // Synced accessors

    private byte[] lastStatementHash = null;

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (tile != null && tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            buildcraft.lib.statement.FullStatement<IFillerPattern> stat = getPatternStatement();
            if (stat != null) {
                io.netty.buffer.ByteBuf temp = io.netty.buffer.Unpooled.buffer();
                buildcraft.lib.net.PacketBufferBC bcBuf = new buildcraft.lib.net.PacketBufferBC(temp);
                stat.writeToBuffer(bcBuf);
                byte[] current = new byte[temp.readableBytes()];
                temp.readBytes(current);
                temp.release();
                
                if (lastStatementHash == null || !java.util.Arrays.equals(lastStatementHash, current)) {
                    lastStatementHash = current;
                    sendMessage(NET_STATEMENT, (buf) -> {
                        buf.writeBytes(current);
                    });
                }
            }
        }
    }

    public boolean getSyncedCanExcavate() {
        return canExcavate.get();
    }

    public boolean getSyncedPreview() {
        return preview.get();
    }

    public boolean getSyncedFinished() {
        return finished.get();
    }

    public boolean getSyncedLocked() {
        return locked.get();
    }

    public IControllable.Mode getSyncedMode() {
        int ordinal = mode.get();
        IControllable.Mode[] values = IControllable.Mode.values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return IControllable.Mode.ON;
    }

    public int getSyncedToPlace() {
        return toPlace.get();
    }

    public int getSyncedToBreak() {
        return toBreak.get();
    }
}
