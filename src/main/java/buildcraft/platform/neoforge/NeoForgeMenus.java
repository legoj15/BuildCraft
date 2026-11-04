/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import buildcraft.platform.menu.BCMenus;
import buildcraft.platform.menu.BufferMenuFactory;

/**
 * {@link BCMenus} on NeoForge: {@link IMenuTypeExtension#create} and the two-argument
 * {@code ServerPlayer.openMenu(MenuProvider, Consumer)}, whose writer bytes reach the factory as its buffer.
 */
public final class NeoForgeMenus implements BCMenus {
    @Override
    public <T extends AbstractContainerMenu> MenuType<T> create(BufferMenuFactory<T> factory) {
        return IMenuTypeExtension.create(factory::create);
    }

    @Override
    public void open(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
        // The provider's own writeClientSideData would run before the writer; a BuildCraft provider writes the
        // same opening data there (for the spectator path, which opens it with no writer), so hide it behind a
        // delegate that keeps only the vanilla half and the close-on-open flag.
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }

            @Override
            public boolean shouldTriggerClientSideContainerClosingOnOpen() {
                return provider.shouldTriggerClientSideContainerClosingOnOpen();
            }
        }, writer);
    }
}
//?}
