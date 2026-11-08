/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.mixin;

//? if fabric {
/*import org.spongepowered.asm.mixin.Mixin;

/^*
 * NeoForge's {@code canConnectRedstone} for the wire's direct neighbour, before 26.3: a {@code RedstoneConnectBlock}
 * (a pipe, which connects where a plug says so) decides whether the wire runs to its side, in place of vanilla's
 * "any signal source" test. From 26.3 vanilla asks the block itself ({@code shouldRedstoneWireConnectTo}), so this
 * mixin is empty there.
 *^/
//? if >=26.3 {
@Mixin(net.minecraft.world.level.block.RedstoneWireBlock.class)
//?} else {
/^@Mixin(net.minecraft.world.level.block.RedStoneWireBlock.class)
^///?}
abstract class RedStoneWireBlockMixin {
    //? if <26.3 {
    /^@com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getConnectingSide(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Lnet/minecraft/world/level/block/state/properties/RedstoneSide;",
            at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private net.minecraft.world.level.block.state.properties.RedstoneSide buildcraft$connectHook(
            net.minecraft.world.level.block.state.properties.RedstoneSide side, net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction, boolean canConnectUp) {
        // An UP connection climbs onto the block above, which the neighbour's own answer does not decide.
        if (side == net.minecraft.world.level.block.state.properties.RedstoneSide.UP) {
            return side;
        }
        net.minecraft.core.BlockPos relativePos = pos.relative(direction);
        net.minecraft.world.level.block.state.BlockState relative = level.getBlockState(relativePos);
        if (relative.getBlock() instanceof buildcraft.platform.hook.RedstoneConnectBlock hook) {
            return hook.canConnectRedstone(relative, level, relativePos, direction)
                    ? net.minecraft.world.level.block.state.properties.RedstoneSide.SIDE
                    : net.minecraft.world.level.block.state.properties.RedstoneSide.NONE;
        }
        return side;
    }
    ^///?}
}
*///?}
