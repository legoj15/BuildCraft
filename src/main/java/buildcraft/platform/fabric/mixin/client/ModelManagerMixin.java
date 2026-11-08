/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin.client;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.10 {
/^import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.profiling.ProfilerFiller;

import buildcraft.platform.fabric.client.FabricBakedModelEdits;
^///?}
import net.minecraft.client.resources.model.ModelManager;

/^*
 * NeoForge's {@code ModelEvent.ModifyBakingResult} on Fabric 1.21.1, at the same point: once the bakery has baked
 * every top-level model and before the model manager reads them out, BuildCraft's queued edits run over the bakery's
 * baked map ({@code FabricBakedModelEdits}). This class carries nothing on the later lines, whose Fabric API has
 * per-model after-bake modifiers for every edit BuildCraft makes.
 *^/
@Mixin(ModelManager.class)
abstract class ModelManagerMixin {

    //? if <1.21.10 {
    /^@Inject(method = "loadModels", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/ModelBakery;bakeModels(Lnet/minecraft/client/resources/model/ModelBakery$TextureGetter;)V",
            shift = At.Shift.AFTER))
    private void buildcraft$modifyBakingResult(ProfilerFiller profiler,
            java.util.Map<net.minecraft.resources.Identifier,
                    net.minecraft.client.resources.model.AtlasSet.StitchResult> atlases,
            ModelBakery bakery, CallbackInfoReturnable<?> cir) {
        FabricBakedModelEdits.run(bakery.getBakedTopLevelModels());
    }
    ^///?}
}
*///?}
