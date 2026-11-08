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

//? if >=26.1 {
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
//?} else {
/^import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
^///?}

import buildcraft.platform.menu.BCMenus;
import buildcraft.platform.menu.BufferMenuFactory;

/^*
 * {@link BCMenus} on Fabric: an {@code ExtendedMenuType} whose opening data is the writer's raw bytes, sent as one
 * {@link ByteBufCodecs#BYTE_ARRAY} and unwrapped into a {@link RegistryFriendlyByteBuf} for the factory, so the
 * factory reads the same bytes it reads on NeoForge. (Fabric API before 26.1 names the two types
 * {@code ExtendedScreenHandlerType} and {@code ExtendedScreenHandlerFactory}.)
 *
 * <p>Fabric refuses to open an extended menu type through a provider that is not an {@code ExtendedMenuProvider}, and
 * vanilla's spectator path ({@code ServerPlayerGameMode} calling {@code openMenu(blockEntity)}) hands it the block
 * entity itself. The {@code ServerPlayer#openMenu} mixin ({@code mixin.ServerPlayerMixin}, the route of NeoForge's
 * {@code writeClientSideData}) wraps such a provider with {@link #withOpeningData} first, so a spectator opens the GUI
 * read-only as on NeoForge.
 *^/
public final class FabricMenus implements BCMenus {
    @Override
    public <T extends AbstractContainerMenu> MenuType<T> create(BufferMenuFactory<T> factory) {
        //? if >=26.1 {
        return new ExtendedMenuType<>((containerId, inventory, data) -> factory.create(containerId, inventory,
            new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), inventory.player.registryAccess())),
            ByteBufCodecs.BYTE_ARRAY);
        //?} else {
        /^return new ExtendedScreenHandlerType<>((containerId, inventory, data) -> factory.create(containerId, inventory,
            new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), inventory.player.registryAccess())),
            ByteBufCodecs.BYTE_ARRAY);
        ^///?}
    }

    @Override
    public void open(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
        player.openMenu(withOpeningData(provider, writer));
    }

    /^* Whether {@code provider} already carries an extended menu type's opening data. *^/
    public static boolean isExtended(MenuProvider provider) {
        //? if >=26.1 {
        return provider instanceof ExtendedMenuProvider<?>;
        //?} else {
        /^return provider instanceof ExtendedScreenHandlerFactory<?>;
        ^///?}
    }

    /^* {@code provider}, opening its menu with {@code writer}'s bytes as the extended menu type's data. *^/
    public static MenuProvider withOpeningData(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
        //? if >=26.1 {
        return new ExtendedMenuProvider<byte[]>() {
        //?} else {
        /^return new ExtendedScreenHandlerFactory<byte[]>() {
        ^///?}
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
        };
    }
}
*///?}
