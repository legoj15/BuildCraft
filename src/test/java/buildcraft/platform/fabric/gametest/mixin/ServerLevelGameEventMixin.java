/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.gametest.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import buildcraft.lib.test.LoaderTestEvents;

/^*
 * NeoForge's {@code VanillaGameEvent} for the game tests on Fabric (the game-test mod only, never the shipped jar):
 * every game event a server level dispatches, before its listeners hear it, is shown to the observers
 * {@link LoaderTestEvents#observeGameEvents} opened.
 *^/
@Mixin(ServerLevel.class)
abstract class ServerLevelGameEventMixin {

    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
            at = @At("HEAD"))
    private void buildcraft$observe(Holder<GameEvent> event, Vec3 position, GameEvent.Context context,
                                    CallbackInfo ci) {
        LoaderTestEvents.FabricGameEvents.post(event, position, context.sourceEntity());
    }
}
*///?}
