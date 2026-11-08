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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.platform.fabric.client.FabricClientHooks;
import buildcraft.platform.hook.FullUpdateTagBlockEntity;
^///?}
import net.minecraft.client.multiplayer.ClientPacketListener;

/^*
 * Two client packet hooks before 1.21.10. NeoForge's {@code RecipesUpdatedEvent} on the lines that still sync the
 * whole recipe list (Fabric API has no recipe-sync event there): fired once the client's recipe manager holds the new
 * list. And BuildCraft's NeoForge {@code onDataPacket} override: an empty block-entity update tag, which vanilla
 * skips, is applied to a {@link FullUpdateTagBlockEntity}. This class carries nothing on the later lines, whose Fabric
 * API has {@code ClientRecipeSynchronizedEvent}.
 *^/
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {

    //? if <1.21.10 {
    /^@Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void buildcraft$recipesReceived(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        FabricClientHooks.fireRecipesReceived(((ClientPacketListener) (Object) this).getRecipeManager().getRecipes());
    }

    // The handler first hops to the main thread (it throws on the network thread before reaching here), so this runs
    // once, on the client thread, after vanilla's own non-empty apply.
    @Inject(method = "handleBlockEntityData", at = @At("TAIL"))
    private void buildcraft$applyEmptyUpdateTag(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !packet.getTag().isEmpty()) {
            return;
        }
        BlockEntity be = mc.level.getBlockEntity(packet.getPos());
        if (be instanceof FullUpdateTagBlockEntity && be.getType() == packet.getType()) {
            be.loadWithComponents(packet.getTag(), ((ClientPacketListener) (Object) this).registryAccess());
        }
    }
    ^///?}
}
*///?}
