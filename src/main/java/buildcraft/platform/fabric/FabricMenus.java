/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.function.Consumer;

import io.netty.buffer.Unpooled;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;

import buildcraft.platform.menu.BCMenus;
import buildcraft.platform.menu.BufferMenuFactory;

/^*
 * {@link BCMenus} on Fabric: an {@link ExtendedMenuType} whose opening data is the writer's raw bytes, sent as one
 * {@link ByteBufCodecs#BYTE_ARRAY} and unwrapped into a {@link RegistryFriendlyByteBuf} for the factory, so the
 * factory reads the same bytes it reads on NeoForge.
 *
 * <p>Gap: Fabric refuses to open an extended menu type through a provider that is not an {@link ExtendedMenuProvider},
 * and vanilla's spectator path ({@code ServerPlayerGameMode} calling {@code openMenu(blockEntity)}) hands it the block
 * entity itself. A spectator right-clicking a BuildCraft machine therefore throws there instead of opening the GUI
 * read-only, until the block entities' NeoForge {@code writeClientSideData} hook gets a Fabric route.
 *^/
public final class FabricMenus implements BCMenus {
    @Override
    public <T extends AbstractContainerMenu> MenuType<T> create(BufferMenuFactory<T> factory) {
        return new ExtendedMenuType<>((containerId, inventory, data) -> factory.create(containerId, inventory,
            new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), inventory.player.registryAccess())),
            ByteBufCodecs.BYTE_ARRAY);
    }

    @Override
    public void open(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
        player.openMenu(new ExtendedMenuProvider<byte[]>() {
            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }

            @Override
            public boolean shouldCloseCurrentScreen() {
                return provider.shouldCloseCurrentScreen();
            }

            @Override
            public byte[] getScreenOpeningData(ServerPlayer target) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), target.registryAccess());
                try {
                    writer.accept(buf);
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return data;
                } finally {
                    buf.release();
                }
            }
        });
    }
}
*///?}
