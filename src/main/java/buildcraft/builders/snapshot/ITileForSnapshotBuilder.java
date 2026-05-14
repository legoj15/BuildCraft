/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.builders.snapshot;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.IPlayerOwned;
import buildcraft.api.mj.MjBattery;

public interface ITileForSnapshotBuilder extends IPlayerOwned {
    Level getWorldBC();

    MjBattery getBattery();

    BlockPos getBuilderPos();

    boolean canExcavate();

    SnapshotBuilder<?> getBuilder();

    default EnumFluidHandlingMode getFluidMode() {
        return EnumFluidHandlingMode.NO_REPLACE;
    }

    /**
     * Tool wielded by the snapshot break-laser for {@link BlockUtil#breakBlockAndGetDropsWithXp}.
     * Determines which blocks yield drops and how much XP: an iron pickaxe lets stone/iron-tier
     * ores drop while {@code NEEDS_DIAMOND_TOOL} blocks still destroy but produce nothing
     * (obsidian, ancient debris, …); a diamond pickaxe lets everything drop.
     * <p>
     * Default is diamond for backwards compatibility — pre-fix the break path passed a hard-coded
     * diamond pickaxe and never looked at the returned drops. Builder/Filler override to iron to
     * match their recipe-ingredient tier.
     */
    default ItemStack getBreakingTool() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    /**
     * Called after {@link SnapshotBuilder} successfully breaks a block at {@code brokenPos}.
     * Implementers decide what to do with the drops, the XP, and any captured source fluid —
     * insert into a local inventory, eject into the world at {@code brokenPos}, route to
     * adjacent containers, absorb fluid into local tanks, etc.
     * <p>
     * Default is a no-op: the helper's {@code world.destroyBlock(pos, false)} already removed
     * the block without dropping anything in the world, so {@code drops}/{@code capturedFluid}
     * would be lost unless the implementer claims them. {@code xp == 0} means no XP under the
     * wielded tool; {@code drops.isEmpty()} likewise signals a tool-gated block that destroyed
     * without yielding loot. {@code capturedFluid.isEmpty()} is the common case — populated only
     * when the broken block was a {@link net.minecraft.world.level.block.LiquidBlock} source
     * (always 1000 mB of the source fluid).
     */
    default void onBlockBroken(BlockPos brokenPos, List<ItemStack> drops, int xp, FluidStack capturedFluid) {
    }
}
