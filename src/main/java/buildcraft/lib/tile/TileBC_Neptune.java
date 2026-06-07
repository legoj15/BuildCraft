/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.tile;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.tile.item.IBCItemHandler;
import buildcraft.lib.tile.item.ItemHandlerManager;

/**
 * Lightweight stub of the 1.12 TileBC_Neptune.
 * Provides player tracking, owner tracking (with persistence), and item manager hooks
 * needed by ContainerBCTile and the GUI layer. Full networking is deferred.
 */
public abstract class TileBC_Neptune extends BlockEntity {

    protected final ItemHandlerManager itemManager = new ItemHandlerManager(
        (handler, slot, before, after) -> this.setChanged()
    );

    private final Set<Player> usingPlayers = new HashSet<>();
    @Nullable
    private GameProfile owner;

    @SuppressWarnings("this-escape")
    public TileBC_Neptune(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Player tracking (used by ContainerBCTile) ---

    public void onPlayerOpen(Player player) {
        usingPlayers.add(player);
    }

    public void onPlayerClose(Player player) {
        usingPlayers.remove(player);
    }

    public boolean canInteractWith(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    // --- Owner tracking ---

    @Nullable
    public GameProfile getOwner() {
        return owner;
    }

    public void setOwner(@Nullable GameProfile owner) {
        this.owner = owner;
    }

    /**
     * Called by block classes from setPlacedBy() to record the placing player.
     * Matches the 1.12.2 TileBC_Neptune.onPlacedBy() pattern.
     */
    public void onPlacedBy(@Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player) {
            setOwner(player.getGameProfile());
            // Mark the chunk dirty so the owner persists even if the block is never
            // mutated again before the chunk unloads.
            setChanged();
        }
    }

    // --- Owner persistence ---

    // Platform bridge: vanilla's BlockEntity load/save signature differs across the MC-1.21.5 cliff
    // (ValueInput/ValueOutput on 1.21.5+, CompoundTag+HolderLookup.Provider on 1.21.1). It is isolated
    // here; subclasses override the version-neutral writeData/readData hooks below instead.
    //? if >=1.21.10 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        writeData(new BCValueOutput(output));
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        readData(new BCValueInput(input));
    }
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(new BCValueOutput(tag));
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(new BCValueInput(tag));
    }*/
    //?}

    /** Version-neutral write hook. Subclasses override this (NOT saveAdditional) and call {@code super.writeData(out)}. */
    protected void writeData(BCValueOutput out) {
        if (owner != null && GameProfileUtil.getId(owner) != null) {
            out.putString("ownerUUID", GameProfileUtil.getId(owner).toString());
            if (GameProfileUtil.getName(owner) != null) {
                out.putString("ownerName", GameProfileUtil.getName(owner));
            }
        }
    }

    /** Version-neutral read hook. Subclasses override this (NOT loadAdditional) and call {@code super.readData(in)}. */
    protected void readData(BCValueInput in) {
        String uuidStr = in.getStringOr("ownerUUID", "");
        if (!uuidStr.isEmpty()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String name = in.getStringOr("ownerName", "Unknown");
                owner = new GameProfile(uuid, name);
            } catch (IllegalArgumentException e) {
                owner = null;
            }
        }
    }

    // --- Item drops ---

    public void addDrops(NonNullList<ItemStack> toDrop, int fortune) {
        itemManager.addDrops(toDrop);
    }

    @Nullable
    public IBCItemHandler getItemHandler(net.minecraft.core.Direction facing) {
        return itemManager.getItemHandler(facing);
    }

    // --- Network Sync ---

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    //? if <1.21.10 {
    /*// 1.21.1's IBlockEntityExtension.onDataPacket only applies the update tag when it is NON-empty.
    // A tile that serialises to an EMPTY tag in some state (e.g. a tank drained empty, an engine that
    // cleared its buffer) would then never have that state applied on the client — it keeps showing
    // the last non-empty contents until the chunk reloads. Apply the tag unconditionally, matching
    // 26.1.2 (whose onDataPacket has no such guard). 1.21.1-only.
    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt,
            net.minecraft.core.HolderLookup.Provider registries) {
        loadWithComponents(pkt.getTag(), registries);
    }*/
    //?}
}
