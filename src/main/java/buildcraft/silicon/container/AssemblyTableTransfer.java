/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.container;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.connection.ConnectionType;

import buildcraft.lib.compat.jei.JeiTransferUtil;
import buildcraft.lib.tile.item.ItemHandlerSimple;

/**
 * The wire format and server-side move of the JEI "+" transfer into the Assembly Table, kept free of JEI
 * classes so the very same {@link #write} the JEI handler sends with is what a headless test replays into
 * {@link ContainerAssemblyTable#readMessage}.
 *
 * <p>The request is the player's own wish list: "move these stacks from my inventory into the table". The
 * server never trusts it for more than that — {@link #apply} only ever moves stacks the player actually
 * holds (matched on item <em>and</em> components, exactly like the table's recipes see them), debits the
 * player by what was inserted, and the table's input slots accept anything a player could place by hand, so
 * a tampered request can at most rearrange the sender's own items. The one thing the server does bound is
 * the request's size ({@link #MAX_STACKS}).
 */
public final class AssemblyTableTransfer {
    private AssemblyTableTransfer() {}

    /** The table has 12 input slots, so a request naming more distinct stacks than that cannot be a real recipe. */
    public static final int MAX_STACKS = 12;

    /** A decoded transfer request. {@code stacks} is empty when the payload was oversized (or really empty). */
    public record Request(boolean maxTransfer, List<ItemStack> stacks) {}

    /** Writes the request. Every stack goes through {@link ItemStack#OPTIONAL_STREAM_CODEC}, so its data
     *  components travel with it — the server matches the player's stacks on item AND components, and an
     *  ingredient reduced to {@code {id, count}} (the old format) could never be found if it carried any.
     *  The codec needs a registry-aware buffer to resolve registry-backed components (enchantments, ...), hence
     *  {@code registries}: the sender's level on the client, the player's level on the server. */
    public static void write(FriendlyByteBuf buf, RegistryAccess registries, boolean maxTransfer,
            List<ItemStack> stacks) {
        RegistryFriendlyByteBuf rbuf = new RegistryFriendlyByteBuf(buf, registries, ConnectionType.NEOFORGE);
        rbuf.writeByte(maxTransfer ? 1 : 0);
        rbuf.writeVarInt(stacks.size());
        for (ItemStack stack : stacks) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(rbuf, stack);
        }
    }

    /** Reads a request written by {@link #write}. A stack count above {@link #MAX_STACKS} yields an empty
     *  request without decoding any stack; empty stacks are skipped. */
    public static Request read(FriendlyByteBuf buf, RegistryAccess registries) {
        RegistryFriendlyByteBuf rbuf = new RegistryFriendlyByteBuf(buf, registries, ConnectionType.NEOFORGE);
        boolean maxTransfer = rbuf.readByte() != 0;
        int count = rbuf.readVarInt();
        if (count < 0 || count > MAX_STACKS) {
            return new Request(maxTransfer, List.of());
        }
        List<ItemStack> stacks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(rbuf);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return new Request(maxTransfer, stacks);
    }

    /** Moves each requested stack from {@code playerInv} into {@code dest}; returns the total item count moved. */
    public static int apply(Inventory playerInv, ItemHandlerSimple dest, Request request) {
        int moved = 0;
        for (ItemStack want : request.stacks()) {
            int limit = request.maxTransfer() ? Integer.MAX_VALUE : want.getCount();
            moved += JeiTransferUtil.moveMatchingToHandler(playerInv, want, limit, dest);
        }
        return moved;
    }
}
