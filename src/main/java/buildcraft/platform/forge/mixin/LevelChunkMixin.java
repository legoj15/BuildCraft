/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge.mixin;

//? if forge {
/*import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import buildcraft.platform.hook.LevelLifecycleBlockEntity;

/^*
 * NeoForge's moment for a block entity's {@code onLoad} on Forge, for BuildCraft's block entities. NeoForge hands every
 * block entity a chunk registers to {@code Level#addFreshBlockEntities}, so {@code onLoad} runs at the head of the level's
 * next block-entity tick: after that tick's scheduled block ticks, before any block entity ticks. Forge's patch of
 * {@code LevelChunk#addAndRegisterBlockEntity} calls {@code onLoad} on the spot, in the middle of whatever placed the block
 * entity (a block set, a chunk's late block entity, a command), and only its chunk-load path
 * ({@code registerAllBlockEntitiesAfterLevelLoad}) uses the fresh list. BuildCraft's {@code onLoaded} work was written to
 * the NeoForge order (a block entity must not be told it joined before its own block's first tick ran, and a placed one
 * is told before it ticks), and Fabric builds the same order itself ({@code FabricLoaderHookRoutes}); here Forge's own
 * machinery already does it, so the call is only moved onto the fresh list, where NeoForge's code puts it.
 *
 * <p><b>Scope.</b> Only a {@link LevelLifecycleBlockEntity} (BuildCraft's) is deferred: every other mod's block entities
 * keep the {@code onLoad} timing Forge gives them, which they may rely on. Forge's tick-head pass calls
 * {@code onLoad} on every fresh block entity with no check, where NeoForge's skips one that was removed or lost its level
 * meanwhile, so {@code AbstractBCBlockEntity#onLoad} makes that check itself.
 *
 * <p><b>Injection point.</b> The one {@code onLoad} call in {@code addAndRegisterBlockEntity}, wrapped so the original
 * runs for any other block entity. {@code onLoad} is {@code IForgeBlockEntity}'s default method, and the call's owner in
 * the bytecode is {@code BlockEntity}, the receiver's type. The block entity's level is the chunk's: {@code setBlockEntity}
 * set it just before.
 *^/
@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {

    @WrapOperation(method = "addAndRegisterBlockEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntity;onLoad()V"))
    private void buildcraft$joinAtTheNextBlockEntityTick(BlockEntity blockEntity, Operation<Void> original) {
        Level level = blockEntity.getLevel();
        if (blockEntity instanceof LevelLifecycleBlockEntity && level != null) {
            level.addFreshBlockEntities(List.of(blockEntity));
        } else {
            original.call(blockEntity);
        }
    }
}
*///?}
