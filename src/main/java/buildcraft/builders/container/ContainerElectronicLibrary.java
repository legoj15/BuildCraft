/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.builders.container;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.core.BCLog;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.nbt.NbtSquisher;
import buildcraft.lib.net.PacketBufferBC;

import buildcraft.builders.BCBuildersMenuTypes;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.Snapshot;
import buildcraft.builders.tile.TileElectronicLibrary;

public class ContainerElectronicLibrary extends ContainerBCTile<TileElectronicLibrary> {

    // Message IDs
    /** Client→Server: update the selected snapshot key. */
    public static final int NET_SELECTED = 1;
    /** Server→Client: snapshot data from completed download. */
    public static final int NET_DOWNLOAD = 2;
    /** Server→Client: request the client to upload a snapshot. */
    public static final int NET_UPLOAD_REQUEST = 3;
    /** Client→Server: one chunk of snapshot upload data. */
    public static final int NET_UPLOAD_DATA = 4;

    // ContainerData indices
    private static final int DATA_PROGRESS_DOWN = 0;
    private static final int DATA_PROGRESS_UP = 1;
    private static final int DATA_COUNT = 2;

    private final ContainerData data;

    // Server-side accumulator for incoming chunked upload data
    private final List<byte[]> uploadChunks = new ArrayList<>();

    // Client-side constructor (from network)
    public ContainerElectronicLibrary(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerElectronicLibrary(int containerId, Inventory playerInv, TileElectronicLibrary tile) {
        super(BCBuildersMenuTypes.LIBRARY.get(), containerId, playerInv.player, tile);

        if (tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            this.data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case DATA_PROGRESS_DOWN -> tile.progressDown;
                        case DATA_PROGRESS_UP -> tile.progressUp;
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return DATA_COUNT;
                }
            };
        } else {
            this.data = new SimpleContainerData(DATA_COUNT);
        }
        addDataSlots(this.data);

        // Slots matching 1.12.2 layout
        addSlot(new SlotOutput(tile.invDownOut, 0, 175, 57));
        addSlot(new SlotBase(tile.invDownIn, 0, 219, 57));
        addSlot(new SlotBase(tile.invUpIn, 0, 175, 79));
        addSlot(new SlotOutput(tile.invUpOut, 0, 219, 79));

        addFullPlayerInventory(8, 138, playerInv);
    }

    private static TileElectronicLibrary getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        var level = playerInv.player.level();
        if (level != null) {
            var be = level.getBlockEntity(pos);
            if (be instanceof TileElectronicLibrary lib) {
                return lib;
            }
        }
        return null;
    }

    // --- Synced accessors for GUI ---

    public int getSyncedProgressDown() {
        return data.get(DATA_PROGRESS_DOWN);
    }

    public int getSyncedProgressUp() {
        return data.get(DATA_PROGRESS_UP);
    }

    // --- Networking ---

    /** Send the selected snapshot key from client to server. */
    public void sendSelectedToServer(Snapshot.Key selected) {
        sendMessage(NET_SELECTED, buf -> {
            buf.writeByte(selected != null ? 1 : 0);
            if (selected != null) {
                selected.writeToByteBuf(buf);
            }
        });
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        super.readMessage(id, buffer, isClient, ctx);

        if (id == NET_SELECTED && !isClient) {
            tile.selected = (buffer.readByte() != 0) ? new Snapshot.Key(buffer) : null;
            return;
        }

        if (id == NET_DOWNLOAD && isClient) {
            // Client receives snapshot data from server download
            byte[] data = buffer.readByteArray();
            try {
                Snapshot snapshot = Snapshot.readFromNBT(NbtSquisher.expand(new ByteArrayInputStream(data)));
                snapshot.computeKey();
                GlobalSavedDataSnapshots.get(GlobalSavedDataSnapshots.Side.CLIENT).addSnapshot(snapshot);
            } catch (java.io.IOException e) {
                BCLog.logger.warn("[library] Failed to deserialize downloaded snapshot", e);
            }
            return;
        }

        if (id == NET_UPLOAD_REQUEST && isClient) {
            // Server asking client to upload a snapshot
            Snapshot.Key key = new Snapshot.Key(buffer);
            sendSnapshotToServer(key);
            return;
        }

        if (id == NET_UPLOAD_DATA && !isClient) {
            boolean last = (buffer.readByte() != 0);
            byte[] chunk = buffer.readByteArray();
            uploadChunks.add(chunk);
            if (last) {
                assembleUpload();
            }
        }
    }

    /** Client: look up the snapshot in the local library and send it to the server in one or more chunks. */
    private void sendSnapshotToServer(Snapshot.Key key) {
        Snapshot snapshot = GlobalSavedDataSnapshots.get(GlobalSavedDataSnapshots.Side.CLIENT).getSnapshot(key);
        if (snapshot == null) {
            BCLog.logger.warn("[library] Upload requested for unknown snapshot key: " + key);
            return;
        }
        byte[] data;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtSquisher.squishVanilla(Snapshot.writeToNBT(snapshot), baos);
            data = baos.toByteArray();
        } catch (java.io.IOException e) {
            BCLog.logger.warn("[library] Failed to serialize snapshot for upload", e);
            return;
        }

        // Send in 32 KB chunks so we stay within packet limits
        final int CHUNK = 32 * 1024;
        int offset = 0;
        while (offset < data.length) {
            int end = Math.min(offset + CHUNK, data.length);
            final byte[] chunk = java.util.Arrays.copyOfRange(data, offset, end);
            final boolean last = (end >= data.length);
            sendMessage(NET_UPLOAD_DATA, buf -> {
                buf.writeByte(last ? 1 : 0);
                buf.writeByteArray(chunk);
            });
            offset = end;
        }
        // If data was empty, send a final empty chunk
        if (data.length == 0) {
            sendMessage(NET_UPLOAD_DATA, buf -> {
                buf.writeByte(1);
                buf.writeByteArray(new byte[0]);
            });
        }
    }

    /** Server: reassemble accumulated upload chunks and hand off to the tile. */
    private void assembleUpload() {
        int total = uploadChunks.stream().mapToInt(c -> c.length).sum();
        byte[] assembled = new byte[total];
        int pos = 0;
        for (byte[] chunk : uploadChunks) {
            System.arraycopy(chunk, 0, assembled, pos, chunk.length);
            pos += chunk.length;
        }
        uploadChunks.clear();

        try {
            Snapshot snapshot = Snapshot.readFromNBT(NbtSquisher.expand(new ByteArrayInputStream(assembled)));
            snapshot.computeKey();
            tile.onUploadReceived(snapshot);
        } catch (java.io.IOException e) {
            BCLog.logger.warn("[library] Failed to deserialize uploaded snapshot", e);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
