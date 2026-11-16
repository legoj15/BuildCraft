/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.platform.hook.PickStackBlock;

/^*
 * NeoForge's player-aware {@code getCloneItemStack} (pick block, the middle click) on Forge 26.x, where no Forge hook
 * carries the player: the server's pick-from-block handler asks the block state for its item with no player (Forge
 * 1.21.1 has {@code IForgeBlock#getCloneItemStack(state, target, level, pos, player)}, and its arm is written). A
 * {@link PickStackBlock} is asked for the stack instead, with the picking player, and a null answer falls back to
 * vanilla's, as NeoForge's override does.
 *
 * <p><b>Injection point.</b> The call to {@code BlockState.getCloneItemStack(LevelReader, BlockPos, boolean)} inside
 * {@code handlePickItemFromBlock}: the one call the method makes to find the item, wrapped (not redirected, so another
 * mod's wrap composes). Forge's patch of this method adds nothing (the handler is vanilla's, as are the creative-mode
 * data and slot steps around the call), and the target is a named call with no ordinal or captured local; the
 * player is the listener's own public field.
 *^/
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @WrapOperation(method = "handlePickItemFromBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getCloneItemStack(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack buildcraft$pickedStack(BlockState state, LevelReader level, BlockPos pos, boolean includeData,
                                             Operation<ItemStack> original) {
        if (state.getBlock() instanceof PickStackBlock block) {
            ItemStack picked = block.pickedStack(level, pos, player);
            if (picked != null) {
                return picked;
            }
        }
        return original.call(state, level, pos, includeData);
    }
}
*///?}
