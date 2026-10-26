/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.energy.compat.viewer;

//? if >=26.3 {
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.energy.BCEnergyBlocks;
import buildcraft.energy.tile.TileEngineStone_BC8;

/**
 * The Stirling engine's fuel list, computed by the server and sent to every player (26.3 only).
 *
 * <p>26.3 removed the fuel table a client could read: a fuel's burn time is the {@code COOKING_FUEL} component's
 * datapack provider, resolvable only with a ServerLevel loot context, and the providers are not synced. So the server
 * asks a template engine ({@link TileEngineStone_BC8#burnTime}, exactly what a placed engine would get) on its own
 * thread whenever it syncs datapacks to a player (login and {@code /reload}), and the client keeps the latest list in
 * {@link #received()} for the recipe viewer. JEI does not need it: it copies its own furnace-fuel category instead.
 */
public record StirlingFuelSync(List<StirlingFuel> fuels) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StirlingFuelSync> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("buildcraftunofficial:stirling_fuels"));

    private static final StreamCodec<RegistryFriendlyByteBuf, StirlingFuel> FUEL_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, StirlingFuel::fuel,
            ByteBufCodecs.VAR_INT, StirlingFuel::burnTime,
            StirlingFuel::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, StirlingFuelSync> STREAM_CODEC =
            FUEL_CODEC.apply(ByteBufCodecs.list()).map(StirlingFuelSync::new, StirlingFuelSync::fuels);

    /** The list the server sent last; empty until the first datapack sync. Written on the client's main thread. */
    private static volatile List<StirlingFuel> received = List.of();

    /** The client's copy of the server's Stirling fuels. */
    public static List<StirlingFuel> received() {
        return received;
    }

    public static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, StirlingFuelSync::handle);
    }

    /** Sends the list to the players a datapack sync is for (the one joining, or everyone on {@code /reload}). */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        StirlingFuelSync message = new StirlingFuelSync(compute(event.getPlayerList().getServer().overworld()));
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, message));
    }

    /** Every item with a cooking-fuel component that the Stirling engine would burn, with its burn time. */
    private static List<StirlingFuel> compute(ServerLevel level) {
        List<StirlingFuel> out = new ArrayList<>();
        TileEngineStone_BC8 engine = new TileEngineStone_BC8(BlockPos.ZERO,
                BCEnergyBlocks.ENGINE_STONE.get().defaultBlockState());
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!stack.has(DataComponents.COOKING_FUEL)) continue;
            int burnTime = TileEngineStone_BC8.burnTime(level, engine, stack);
            if (burnTime > 0) {
                out.add(new StirlingFuel(stack, burnTime));
            }
        }
        return out;
    }

    private static void handle(StirlingFuelSync message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> received = List.copyOf(message.fuels()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
//?}
