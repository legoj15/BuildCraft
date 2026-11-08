/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
//? if <26.1 {
/^import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;

import buildcraft.platform.fabric.FabricGameHooks;
^///?}
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/^*
 * NeoForge's entity-join point for entities a server level adds from a chunk or as new ({@code addEntity}, before the
 * entity is added; a refusal keeps it out), on the lines whose Fabric API has no event there ({@code ServerEntityEvents.ALLOW_LOAD} arrived
 * with 26.1; this class carries nothing on those lines). A player joins through {@code ServerLevelMixin}.
 *^/
@Mixin(PersistentEntitySectionManager.class)
abstract class PersistentEntitySectionManagerMixin {

    //? if <26.1 {
    /^@Inject(method = "addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z", at = @At("HEAD"),
            cancellable = true)
    private void buildcraft$entityJoin(EntityAccess access, boolean loadedFromDisk,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (access instanceof Entity entity && entity.level() instanceof ServerLevel level
                && !FabricGameHooks.fireEntityJoin(entity, level, loadedFromDisk)) {
            // Refused: not added, as NeoForge's cancelled join event leaves it.
            cir.setReturnValue(false);
        }
    }
    ^///?}
}
*///?}
