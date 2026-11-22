/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin.client;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.10 {
/^import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraftforge.client.loading.ClientModLoader;
import net.minecraftforge.common.MinecraftForge;

import buildcraft.api.core.BCLog;
^///?}
import net.minecraft.client.quickplay.QuickPlay;

/^*
 * Starts Forge's game event bus before a quick-play join loads a world, on the 1.21.1 line only (Forge 52, EventBus 6;
 * docs/forge-port.md, "Quick-play on Forge 1.21.1"). Forge creates {@code MinecraftForge.EVENT_BUS} shut and
 * {@code ClientModLoader.completeModLoading} starts it, but Forge 52's {@code Minecraft} patch calls
 * {@code onResourceLoadFinished}, and through it this method, first: the join ({@code openWorld}) runs the data-pack
 * reload on the main thread before it returns, the reload posts Forge's own {@code AddReloadListenerEvent} on the shut
 * bus, the post is dropped, the loot modifier manager is never set, and the integrated server dies at its first loot
 * roll ("Can not retrieve LootModifierManager until resources have loaded once"). Forge's ordering, not BuildCraft's:
 * every Forge 1.21.1 mod and any launcher's "join world on start" meets it.
 *
 * <p><b>Injection point.</b> The head of the static {@code QuickPlay.connect}, the one entry of the three quick-play
 * kinds (singleplayer, multiplayer, Realms), on the stack of the failing reload and before any event is lost. It is
 * named by name alone, so the parameters do not matter (26.3 hands it a {@code QuickPlayVariant}, not a
 * {@code QuickPlayData}). The other two kinds pass through the same head to no effect: {@code start()} only clears
 * EventBus 6's shutdown flag, so the start that follows in {@code completeModLoading} is a no-op, and the events a
 * quick-played session now delivers are the ones every title-screen launch already delivers.
 *
 * <p><b>Guard.</b> Forge starts the bus in {@code completeModLoading} only when mod loading had no error, and a failed
 * load must still end on its error screen as today: the bus is started only when {@code ClientModLoader}'s private
 * {@code error} field is null, Forge's own test. No public accessor was identified for that field, so it is read
 * reflectively, as {@code ClassicSlotLedger} reads {@code SidedInvWrapper}'s protected fields; a field that cannot be
 * read answers nothing and leaves the bus as Forge has it (one {@code [platform]} WARN), so a newer Forge that renames
 * it falls back on the {@code quickplay_drops_game_events} workaround instead of starting a bus for a half-loaded game.
 *
 * <p>From 1.21.11 up Forge is on EventBus 7, whose buses are never shut: there this is an empty {@code @Mixin} on the
 * same class, which exists there too. This class carries nothing from 1.21.11 up.
 *^/
//? if <1.21.10 {
/^@Mixin(QuickPlay.class)
abstract class QuickPlayMixin {
    @Inject(method = "connect", at = @At("HEAD"))
    private static void buildcraft$startGameBus(CallbackInfo ci) {
        try {
            Field error = ClientModLoader.class.getDeclaredField("error");
            error.setAccessible(true);
            if (error.get(null) == null) {
                MinecraftForge.EVENT_BUS.start();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            BCLog.logger.warn("[platform] cannot read ClientModLoader.error; "
                    + "MinecraftForge.EVENT_BUS is left to Forge", e);
        }
    }
}
^///?} else {
@Mixin(QuickPlay.class)
abstract class QuickPlayMixin {
}
//?}
*///?}
