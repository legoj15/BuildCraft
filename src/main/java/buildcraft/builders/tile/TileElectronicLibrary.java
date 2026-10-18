/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.tile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.enums.EnumSnapshotType;

import buildcraft.lib.gui.IBCMenuProvider;
import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.nbt.NbtSquisher;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.lib.tile.item.ItemHandlerManager.EnumAccess;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.builders.BCBuildersBlockEntities;
import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.container.ContainerElectronicLibrary;
import buildcraft.builders.item.ItemSnapshot;
import buildcraft.builders.snapshot.GlobalSavedDataSnapshots;
import buildcraft.builders.snapshot.Snapshot;

public class TileElectronicLibrary extends TileBC_Neptune implements IBCMenuProvider {

    /** Accepts used snapshot items for downloading (copying snapshot from server DB to client library). */
    public final ItemHandlerSimple invDownIn = itemManager.addInvHandler(
        "downIn", 1,
        (slot, stack) -> stack.getItem() instanceof ItemSnapshot snap && snap.isUsed(),
        EnumAccess.INSERT, EnumPipePart.VALUES
    );
    /** Output slot for the download operation. */
    public final ItemHandlerSimple invDownOut = itemManager.addInvHandler(
        "downOut", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES
    );
    /** Accepts any snapshot item for uploading (writing a selected snapshot onto the item). */
    public final ItemHandlerSimple invUpIn = itemManager.addInvHandler(
        "upIn", 1,
        (slot, stack) -> stack.getItem() instanceof ItemSnapshot,
        EnumAccess.INSERT, EnumPipePart.VALUES
    );
    /** Output slot for the upload operation. */
    public final ItemHandlerSimple invUpOut = itemManager.addInvHandler(
        "upOut", 1, EnumAccess.EXTRACT, EnumPipePart.VALUES
    );

    /** The currently selected snapshot key (selected by the player in the GUI). */
    @Nullable
    public Snapshot.Key selected = null;

    /** Progress counters: -1 = idle, 0..49 = working, >=50 = done. */
    public int progressDown = -1;
    public int progressUp = -1;

    /** Ticks an asked-for upload may go without a chunk arriving before the tile asks again (the asker may have
     *  closed the menu, or not have the snapshot). */
    static final int UPLOAD_ANSWER_TIMEOUT_TICKS = 100;

    /** Players currently viewing this tile's GUI (server-side tracking for network broadcasts). */
    private final Set<Player> watchingPlayers = new HashSet<>();

    /** The key the last upload request asked the watchers for, while it is still wanted: an upload is accepted only
     *  against it, so a client cannot write an item no request asked for, nor answer one request twice. Not saved:
     *  a reload asks again. */
    @Nullable
    private Snapshot.Key uploadRequested;
    /** Ticks left before an unanswered {@link #uploadRequested} is asked again. */
    private int uploadWaitTicks;
    /** A key whose upload was refused (too large, unreadable, the wrong build): not asked for again until the
     *  selection or the blank changes, so one bad upload is not re-sent and re-parsed every few seconds. */
    @Nullable
    private Snapshot.Key uploadRefused;
    /** The watchers whose answer to a request for {@link #uploadRefusedByKey} was refused: a bad stream from one
     *  watcher ends only that watcher's answer, not the request, so the others can still satisfy it. They are not
     *  asked for that build again, and their further chunks for it are ignored, until the blank or the selection
     *  changes; once every watcher is in here the build itself is refused ({@link #uploadRefused}). */
    private final Set<Player> uploadRefusedBy = new HashSet<>();
    @Nullable
    private Snapshot.Key uploadRefusedByKey;

    public TileElectronicLibrary(BlockPos pos, BlockState state) {
        super(BCBuildersBlockEntities.LIBRARY.get(), pos, state);
    }

    @Override
    protected boolean spillsContentsOnRemoval() {
        return true;
    }

    @Override
    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        super.onPlacedBy(placer, stack);   // sets owner on the parent TileBC_Neptune
        if (level != null && !level.isClientSide()) {
            setChanged();
            // Sync the tile entity update tag (which includes owner) to nearby clients so
            // the Owner ledger can display the placer's name/skin immediately on first open.
            // No-re-mesh push: owner is GUI-only data, the block model is unchanged.
            markForGuiUpdate();
        }
    }

    @Override
    public void onPlayerOpen(Player player) {
        super.onPlayerOpen(player);
        watchingPlayers.add(player);
    }

    @Override
    public void onPlayerClose(Player player) {
        super.onPlayerClose(player);
        watchingPlayers.remove(player);
        // A watcher that leaves can no longer answer: the request may now have nobody left who could.
        uploadRefusedBy.remove(player);
        refuseIfNoWatcherCanAnswer();
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // --- Download: copy a used snapshot from server DB to client library ---
        if (!invDownIn.getStackInSlot(0).isEmpty() && invDownOut.getStackInSlot(0).isEmpty()) {
            if (progressDown == -1) {
                progressDown = 0;
            }
            if (progressDown >= 50) {
                ItemStack inStack = invDownIn.getStackInSlot(0);
                // Move item to output
                invDownOut.setStackInSlot(0, inStack.copy());
                invDownIn.setStackInSlot(0, ItemStack.EMPTY);
                progressDown = -1;
                setChanged();
                // Send snapshot data to all watching clients
                broadcastDownload(inStack);
            } else {
                progressDown++;
            }
        } else if (progressDown != -1) {
            progressDown = -1;
        }

        // --- Upload: request snapshot data from client, then write to server DB ---
        if (invUpIn.getStackInSlot(0).isEmpty()) {
            // a new blank is a new try
            uploadRefused = null;
            clearWatcherRefusals();
        }
        if (!canTakeUpload() || selected.equals(uploadRefused)) {
            withdrawUploadRequest();
            progressUp = -1;
        } else if (uploadRequested != null && !uploadRequested.equals(selected)) {
            // The selection changed while waiting: the old answer is no longer wanted.
            withdrawUploadRequest();
            progressUp = 0;
        } else if (uploadRequested != null) {
            if (--uploadWaitTicks <= 0) {
                withdrawUploadRequest(); // nobody answered: count up and ask again
                progressUp = 0;
            }
        } else {
            if (progressUp == -1) {
                progressUp = 0;
            }
            if (progressUp >= 50) {
                // Signal all watching clients to send us the selected snapshot, and wait for the answer.
                if (requestUpload()) {
                    uploadRequested = selected;
                    uploadWaitTicks = UPLOAD_ANSWER_TIMEOUT_TICKS;
                } else {
                    progressUp = -1;
                }
            } else {
                progressUp++;
            }
        }
    }

    /** Whether an upload could be turned into an item now: something selected, a blank in, the output free. */
    private boolean canTakeUpload() {
        return selected != null && !invUpIn.getStackInSlot(0).isEmpty() && invUpOut.getStackInSlot(0).isEmpty();
    }

    /** Whether an upload request is out and still wanted; the menu keeps no upload chunk while it is not. */
    public boolean isUploadRequested() {
        return uploadRequested != null;
    }

    /** Whether the selected snapshot's upload was refused and is not being asked for again. */
    public boolean isUploadRefused() {
        return selected != null && selected.equals(uploadRefused);
    }

    /** An upload chunk for the outstanding request arrived: a large upload in flight is not timed out. */
    public void onUploadChunk() {
        if (uploadRequested != null) uploadWaitTicks = UPLOAD_ANSWER_TIMEOUT_TICKS;
    }

    /** Whether {@code player}'s answer to the outstanding request was already refused: the menu ignores the rest of
     *  its chunks for it. */
    public boolean hasRefusedUpload(Player player) {
        return uploadRequested != null && uploadRequested.equals(uploadRefusedByKey)
            && uploadRefusedBy.contains(player);
    }

    /**
     * {@code from}'s upload for the outstanding request could not be used. That ends {@code from}'s answer and
     * nothing else: the request stays out for the other watchers, and only once none of them is left to answer is the
     * build refused and not asked for again (see {@link #uploadRefused}).
     */
    public void onUploadRefused(Player from, String why) {
        if (uploadRequested == null) return;
        BCLog.logger.warn("[library] Refused the upload of {} at {} from {}: {}", uploadRequested, worldPosition,
            from.getName().getString(), why);
        if (!uploadRequested.equals(uploadRefusedByKey)) {
            uploadRefusedBy.clear();
            uploadRefusedByKey = uploadRequested;
        }
        uploadRefusedBy.add(from);
        refuseIfNoWatcherCanAnswer();
    }

    /** Refuse the build when every watcher has had its answer refused (a watcher that left no longer counts). */
    private void refuseIfNoWatcherCanAnswer() {
        if (uploadRequested == null || uploadRefusedBy.isEmpty() || !uploadRequested.equals(uploadRefusedByKey)) return;
        for (Player watcher : watchingPlayers) {
            if (!uploadRefusedBy.contains(watcher)) return;
        }
        uploadRefused = uploadRequested;
        withdrawUploadRequest();
        progressUp = -1;
    }

    private void clearWatcherRefusals() {
        uploadRefusedBy.clear();
        uploadRefusedByKey = null;
    }

    /** The request is over (withdrawn, timed out, refused or answered): stop taking chunks for it and free what the
     *  watchers' menus hold of a half-sent answer, which would otherwise stay until their next chunk or until they
     *  close. */
    private void withdrawUploadRequest() {
        if (uploadRequested == null) return;
        uploadRequested = null;
        for (Player p : watchingPlayers) {
            if (p.containerMenu instanceof ContainerElectronicLibrary container) {
                container.releaseUpload();
            }
        }
    }

    /**
     * Look up the snapshot on the server using the key embedded in the used item, then serialize
     * and send it to all watching clients so they can add it to their local library.
     */
    private void broadcastDownload(ItemStack usedItem) {
        Snapshot.Header header = ItemSnapshot.getHeader(usedItem);
        if (header == null) return;

        Snapshot snapshot = GlobalSavedDataSnapshots.get(level).getSnapshot(header.key);
        if (snapshot == null) return;

        // Attach the header to the snapshot key before sending so the client can display metadata
        Snapshot snapshotWithHeader = snapshot.copy();
        snapshotWithHeader.key = new Snapshot.Key(snapshot.key, header);

        byte[] data;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtSquisher.squishVanilla(Snapshot.writeToNBT(snapshotWithHeader), baos);
            data = baos.toByteArray();
        } catch (IOException e) {
            BCLog.logger.warn("[library] Failed to serialize snapshot for download broadcast", e);
            return;
        }

        for (Player p : watchingPlayers) {
            if (p.containerMenu instanceof ContainerElectronicLibrary container) {
                container.sendMessage(ContainerElectronicLibrary.NET_DOWNLOAD, buf -> buf.writeByteArray(data));
            }
        }
    }

    /**
     * Send an upload request to all watching clients. Each client will look up the selected
     * snapshot in their local library and send it back to the server.
     */
    private boolean requestUpload() {
        if (selected == null) return false;
        final Snapshot.Key key = selected;
        boolean asked = false;
        boolean knownBad = key.equals(uploadRefusedByKey);
        for (Player p : watchingPlayers) {
            // A watcher whose answer for this build was refused is not asked for it again.
            if (knownBad && uploadRefusedBy.contains(p)) continue;
            if (p.containerMenu instanceof ContainerElectronicLibrary container) {
                // A fresh request: whatever an earlier one left in this watcher's menu is not part of the answer.
                container.beginUploadRequest();
                container.sendMessage(ContainerElectronicLibrary.NET_UPLOAD_REQUEST, buf -> key.writeToByteBuf(buf));
                asked = true;
            }
        }
        return asked;
    }

    /**
     * Called by ContainerElectronicLibrary when a client has uploaded snapshot data. Accepted only as the answer to
     * the outstanding request, for the build it asked for, while a blank waits and the output is free: then the
     * snapshot goes into the server DB and the used item into the output slot. Anything else is ignored (an
     * unasked-for or second answer) or refused (the wrong build).
     *
     * @param from the watcher who sent it
     * @return whether the upload became an item
     */
    public boolean onUploadReceived(Player from, Snapshot snapshot) {
        if (uploadRequested == null || !canTakeUpload()) {
            BCLog.logger.warn("[library] Ignoring an upload at {} that no request is waiting for", worldPosition);
            return false;
        }
        if (snapshot.key == null || !java.util.Arrays.equals(snapshot.key.hash, uploadRequested.hash)) {
            onUploadRefused(from, "it is not the snapshot that was asked for");
            return false;
        }
        Snapshot.Header asked = uploadRequested.header;
        withdrawUploadRequest(); // answered: other watchers' half-sent answers are not wanted either
        clearWatcherRefusals();
        progressUp = -1;
        Snapshot.Header header = storeUpload(GlobalSavedDataSnapshots.get(level), snapshot, asked);
        EnumSnapshotType type = snapshot.getType();
        ItemSnapshot usedItem = type == EnumSnapshotType.BLUEPRINT
            ? BCBuildersItems.BLUEPRINT_USED.get()
            : BCBuildersItems.TEMPLATE_USED.get();
        invUpOut.setStackInSlot(0, usedItem.createUsedStack(header));
        invUpIn.setStackInSlot(0, ItemStack.EMPTY);
        setChanged();
        return true;
    }

    /**
     * Store an uploaded snapshot in {@code store}; returns the header for the item that stands for it. The server
     * keeps content only (like the Architect Table does) and the name travels on the item, so one build uploaded
     * under several names shares one file and every resulting item finds it.
     * <p>
     * The item's name, owner and date come from {@code asked}, the header of the request the tile made (checked by
     * {@link ContainerElectronicLibrary#isSelectable} when the client selected it), never from the upload's own
     * header: the upload is matched to the request by content hash only, so its header is whatever the sender wrote
     * (an unbounded name would push the item past the size clients accept for it).
     */
    public static Snapshot.Header storeUpload(GlobalSavedDataSnapshots store, Snapshot snapshot,
                                             @Nullable Snapshot.Header asked) {
        snapshot.key = new Snapshot.Key(snapshot.key, (Snapshot.Header) null);
        store.addSnapshot(snapshot);
        if (asked == null) {
            return new Snapshot.Header(snapshot.key, new UUID(0, 0), new java.util.Date(), "Snapshot");
        }
        return new Snapshot.Header(snapshot.key, asked.owner, asked.created, asked.name);
    }

    // --- NBT persistence ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        output.putInt("progressDown", progressDown);
        output.putInt("progressUp", progressUp);
        output.store("items", CompoundTag.CODEC, itemManager.serializeNBT());
        if (selected != null) {
            output.store("selected", CompoundTag.CODEC, selected.serializeNBT());
        }
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        progressDown = input.getIntOr("progressDown", -1);
        progressUp = input.getIntOr("progressUp", -1);
        input.read("items", CompoundTag.CODEC).ifPresent(itemManager::deserializeNBT);
        selected = input.read("selected", CompoundTag.CODEC)
            .map(Snapshot.Key::new)
            .orElse(null);
    }

    // --- MenuProvider ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraftunofficial.library");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ContainerElectronicLibrary(containerId, playerInv, this);
    }
}
