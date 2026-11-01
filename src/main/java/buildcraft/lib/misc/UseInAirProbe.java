/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The "may this machine use the item it holds in the air?" ask, for the stripes handlers' last resort (the dispenser
 * fallback's right-click with no block target) that a stripes pipe and a stripes robot share. A player's use in the
 * air asks the item right-click event at the player's feet and no more, and the items that touch the world from there
 * (buckets, boats, lily pads, spawn eggs) post no placement or break event of their own at the cells they
 * reach, so a claim or protection mod never hears of them. The vanilla ones all aim along the player's eye ray
 * ({@link Item#getPlayerPOVHitResult}), which a machine's posed player has before the use, so the block each fluid
 * mode's ray meets is known and gets the question the Builder asks for the same act: that block and the cell in front
 * of the face it was met on get the placement probe (a bucket pours into either, a boat or an egg is put down in
 * front); a fluid, or a block a bucket picks up whole (powder snow), gets the break probe (a bucket takes it) and the
 * cell above it the placement probe (a lily pad goes on top). A ray that meets nothing within reach touches no block,
 * and a thrown item ({@link #isThrown}) is the user's own projectile, judged by its impact events, so it is not asked.
 *
 * <p>Who is asked is the caller's: a pipe asks as the owner's player that is already aimed
 * ({@link BlockUtil#canMachinePlaceAs}), a robot asks as its station's owner ({@link BlockUtil#canMachinePlace}).
 * Server thread only, like every caller.
 */
public final class UseInAirProbe {

    private UseInAirProbe() {}

    /**
     * Whether every cell the item {@code player} holds can touch from where it is posed passes {@code mayPlace} (the
     * cells something may be set down in) and {@code mayBreak} (the fluids that may be taken). The placement cells
     * are asked first, in the order the rays meet them, and the first refusal ends the ask.
     *
     * @param player the player the item is about to be used by, already posed (position and aim) as the use will find it
     * @param stack  the item about to be used; a thrown one is let through without an ask
     */
    public static boolean permits(ServerLevel level, Player player, ItemStack stack, Predicate<BlockPos> mayPlace,
            Predicate<BlockPos> mayBreak) {
        if (isThrown(stack.getItem())) {
            return true;
        }
        Set<BlockPos> placed = new LinkedHashSet<>();
        Set<BlockPos> taken = new LinkedHashSet<>();
        for (ClipContext.Fluid fluid : ClipContext.Fluid.values()) {
            BlockHitResult hit = Item.getPlayerPOVHitResult(level, player, fluid);
            if (hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            BlockPos pos = hit.getBlockPos();
            placed.add(pos);
            placed.add(pos.relative(hit.getDirection()));
            // A bucket takes a fluid, or a block that is picked up whole (powder snow): a waterloggable block is
            // only taken from through the fluid it holds, so a dry one is not asked about.
            Block block = level.getBlockState(pos).getBlock();
            if (!level.getFluidState(pos).isEmpty()
                    || (block instanceof BucketPickup && !(block instanceof SimpleWaterloggedBlock))) {
                taken.add(pos);
                placed.add(pos.above());
            }
        }
        for (BlockPos cell : placed) {
            if (!mayPlace.test(cell)) {
                return false;
            }
        }
        for (BlockPos cell : taken) {
            if (!mayBreak.test(cell)) {
                return false;
            }
        }
        return true;
    }

    /** A thrown item (snowball, egg, arrow, potion, ender pearl, fishing bobber, eye of ender) puts down no block of
     *  its own where it aims: what it hits is judged by its own impact events, so its aim is not asked about. Only its
     *  use in the air is the throw: a matched item may still act on a block it is clicked on (a fire charge lights it,
     *  an eye of ender fills a portal frame). */
    public static boolean isThrown(Item item) {
        return item instanceof ProjectileItem || item instanceof EnderpearlItem || item instanceof FishingRodItem
                || item instanceof EnderEyeItem;
    }
}
