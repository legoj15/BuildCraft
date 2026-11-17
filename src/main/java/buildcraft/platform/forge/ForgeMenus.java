/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.OptionalInt;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import net.minecraftforge.common.extensions.IForgeMenuType;

import buildcraft.platform.Platform;
import buildcraft.platform.menu.BCMenus;
import buildcraft.platform.menu.BufferMenuFactory;
import buildcraft.platform.menu.MenuOpeningData;

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
 * a {@code BlockPos} from it and fails on the client network thread). Forge's {@code IContainerFactory} cannot help: it
 * builds the menu through its two-argument {@code create}, whose buffer is {@code null} on exactly that path. So
 * {@code platform.forge.mixin.ServerPlayerMixin} (the route of NeoForge's {@code writeClientSideData}, as Fabric's
 * mixin of the same name is) sends a {@link MenuOpeningData} provider through {@link #openWithOpeningData}. A menu of
 * any other provider (vanilla's, another mod's) still reaches the factory with {@code null}, as on NeoForge.
 *^/
public final class ForgeMenus implements BCMenus {
    @Override
    public <T extends AbstractContainerMenu> MenuType<T> create(BufferMenuFactory<T> factory) {
        return IForgeMenuType.create((containerId, inventory, data) -> {
            // data is null only for a menu opened by a provider that is not a MenuOpeningData (see the class comment).
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

    /^*
     * Vanilla's {@code ServerPlayer.openMenu(MenuProvider)} for a provider that writes its own opening data, called by
     * {@code ServerPlayerMixin} in its place. Forge's two-argument {@code openMenu} does the whole open (close the open
     * menu, number the new one, build it, send the packet that carries the data, init it, post Forge's open event); what
     * it leaves out is vanilla's answer and its spectator message, which are rebuilt here from what it leaves behind:
     * a menu it could not build leaves the player's own inventory menu open.
     *
     * @return the new menu's container id, or empty when the provider built no menu (as vanilla answers)
     ^/
    public static OptionalInt openWithOpeningData(ServerPlayer player, MenuProvider provider, MenuOpeningData data) {
        // As open(): a fake player opens nothing (and has no connection for Forge's openMenu to send on).
        if (Platform.fakePlayers().isFakePlayer(player)) {
            return OptionalInt.empty();
        }
        player.openMenu(provider, buf -> data.writeOpeningData(new RegistryFriendlyByteBuf(buf, player.registryAccess())));
        if (player.containerMenu == player.inventoryMenu) {
            if (player.isSpectator()) {
                //? if >=1.21.10 {
                player.sendOverlayMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED));
                //?} else {
                /^player.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                ^///?}
            }
            return OptionalInt.empty();
        }
        return OptionalInt.of(player.containerMenu.containerId);
    }
}
*///?}
