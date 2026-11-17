/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import java.util.OptionalInt;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.GameType;

import buildcraft.platform.forge.ForgeFakePlayers;
import buildcraft.platform.forge.ForgeMenus;
import buildcraft.platform.menu.MenuOpeningData;

/^*
 * NeoForge's {@code writeClientSideData} on Forge: a {@link MenuOpeningData} provider handed straight to vanilla's
 * {@code ServerPlayer#openMenu(MenuProvider)} (the spectator path of {@code ServerPlayerGameMode#useItemOn}, which Forge
 * adds only an event beside) is opened through Forge's own {@code openMenu(provider, writer)} with the provider's opening
 * data, so the client factory gets the buffer it gets from {@code BCMenus#open} and not the {@code null} Forge's
 * {@code IContainerFactory} defaults to for a menu that arrives through the vanilla packet. The work is
 * {@link ForgeMenus#openWithOpeningData}; this class only routes to it.
 *
 * <p><b>Injection point.</b> The head of {@code openMenu(MenuProvider)}, cancelling with the result. Forge's patch of this
 * method adds one call at the tail (the {@code onPlayerOpenContainer} event, which Forge's two-argument {@code openMenu}
 * posts too), so nothing at the head is touched by it, and a head injection depends on no local variable, ordinal or
 * call target inside the body. The method's own descriptor is vanilla's and Forge cannot change it. A provider that is not
 * a {@code MenuOpeningData} (every vanilla and foreign menu) falls through to vanilla untouched. There is no recursion:
 * Forge's two-argument {@code openMenu} never calls the one-argument one.
 *
 * <p><b>A machine's game mode.</b> The player constructor takes the server's default game mode, which a creative-default
 * server hands to every machine's {@link ForgeFakePlayers} player: no tool wear, no item consumed, instant breaking.
 * NeoForge's patch of {@code calculateGameModeForNewPlayer} and Fabric API's mixin on the same method both answer survival
 * for a fake player; this is the Forge twin, injected at the head of that private method so the player is never creative
 * at all (its abilities are set from the answer in the constructor, and changing the mode afterwards would broadcast a
 * tab-list update for a player nobody lists). Forge's patch leaves the method as vanilla has it; the head depends on
 * no local, ordinal or call target. The test is made while the vanilla constructor is still running, which is why it
 * asks the machine player's own class and not a field. (The 1.21.1 constructor asks no game mode at all: a machine there
 * keeps the game-mode manager's survival default, and the head injection, on the same vanilla method, is never reached
 * for it.)
 *^/
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;", at = @At("HEAD"),
            cancellable = true)
    private void buildcraft$openingData(@Nullable MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        if (provider instanceof MenuOpeningData data) {
            cir.setReturnValue(ForgeMenus.openWithOpeningData((ServerPlayer) (Object) this, provider, data));
        }
    }

    @Inject(method = "calculateGameModeForNewPlayer(Lnet/minecraft/world/level/GameType;)Lnet/minecraft/world/level/GameType;",
            at = @At("HEAD"), cancellable = true)
    private void buildcraft$machinesAreSurvival(@Nullable GameType loaded, CallbackInfoReturnable<GameType> cir) {
        if (ForgeFakePlayers.isMachinePlayer(this)) {
            cir.setReturnValue(GameType.SURVIVAL);
        }
    }
}
*///?}
