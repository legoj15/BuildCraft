/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import net.minecraftforge.common.extensions.IForgeMenuType;

import buildcraft.platform.Platform;
import buildcraft.platform.menu.BCMenus;
import buildcraft.platform.menu.BufferMenuFactory;

/^*
 * {@link BCMenus} on MinecraftForge: {@link IForgeMenuType#create} for the type, and the two-argument
 * {@code ServerPlayer.openMenu(MenuProvider, Consumer<FriendlyByteBuf>)} Forge adds to the server player, whose writer
 * bytes reach the factory as its buffer.
 *
 * <p>Forge hands the factory a plain {@code FriendlyByteBuf} and the writer a plain one, so both are wrapped as
 * {@link RegistryFriendlyByteBuf} here, over the opening player's registries (the registries of the player's own side:
 * the client player's on the factory, the server player's for the writer). Forge's {@code openMenu} also calls no
 * provider hook, so unlike NeoForge no delegate has to hide a {@code writeClientSideData} of the provider's own: the
 * writer is the only thing that writes. It caps the data at 32600 bytes (it throws past that), far over what a
 * BuildCraft container writes.
 *
 * <p><b>The spectator path.</b> Vanilla's {@code ServerPlayer.openMenu(MenuProvider)}, which a spectator clicking a
 * block entity reaches, is not hooked by Forge: it sends the menu with no opening data, and the client builds it with a
 * {@code null} buffer, which the {@link BufferMenuFactory} contract says BuildCraft never produces (a container reads
 * a {@code BlockPos} from it and fails on the client network thread). That needs a mixin on vanilla's {@code openMenu}
 * to open the provider through {@link #open} when it is a {@code MenuOpeningData}; see
 * {@code platform.fabric.mixin.ServerPlayerMixin}, whose route it is. Until then the factory passes the {@code null}
 * through, as NeoForge's does for a menu opened with no data.
 *^/
public final class ForgeMenus implements BCMenus {
    @Override
    public <T extends AbstractContainerMenu> MenuType<T> create(BufferMenuFactory<T> factory) {
        return IForgeMenuType.create((containerId, inventory, data) -> {
            // TODO(#209 F3): needs a Forge mixin for ServerPlayer#openMenu(MenuProvider), see
            // platform/fabric/mixin/ServerPlayerMixin: the vanilla (spectator) path reaches here with data == null.
            RegistryFriendlyByteBuf buf = data == null ? null
                    : new RegistryFriendlyByteBuf(data, inventory.player.registryAccess());
            return factory.create(containerId, inventory, buf);
        });
    }

    @Override
    public void open(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
        // The contract: a fake player opens nothing. NeoForge's FakePlayer overrides openMenu to do that; Forge has no
        // fake-player class to carry the override (the Forge fake players are BuildCraft's own, and other mods'), and
        // a fake player has no client to send the OpenContainer packet to, so the guard sits here.
        if (Platform.fakePlayers().isFakePlayer(player)) {
            return;
        }
        player.openMenu(provider, buf -> writer.accept(new RegistryFriendlyByteBuf(buf, player.registryAccess())));
    }
}
*///?}
