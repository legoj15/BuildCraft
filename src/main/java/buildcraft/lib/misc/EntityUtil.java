/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import javax.annotation.Nullable;

//? if >=26.3 {
import net.minecraft.util.Prediction;
//?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

import net.neoforged.neoforge.common.Tags;

import buildcraft.api.tools.IToolWrench;

import buildcraft.lib.BCLibConfig;

public class EntityUtil {

    /**
     * Whether {@code stack} should act as a wrench. True when it implements BuildCraft's
     * {@link IToolWrench} API, or — when {@link BCLibConfig#allowForeignWrenches} is enabled
     * (the default) — when it carries the {@code c:tools/wrench} convention tag, so a wrench
     * from another mod (e.g. a configurator) works on BuildCraft machinery too.
     */
    public static boolean isWrench(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof IToolWrench) {
            return true;
        }
        return BCLibConfig.allowForeignWrenches.get() && stack.is(Tags.Items.TOOLS_WRENCH);
    }

    /** Returns the hand holding a {@linkplain #isWrench wrench}, or {@code null} if neither hand holds one. */
    @Nullable
    public static InteractionHand getWrenchHand(LivingEntity entity) {
        if (isWrench(entity.getItemInHand(InteractionHand.MAIN_HAND))) {
            return InteractionHand.MAIN_HAND;
        }
        if (isWrench(entity.getItemInHand(InteractionHand.OFF_HAND))) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /**
     * Fires the wrench's post-use callback. BuildCraft's own wrench (and any addon implementing
     * {@link IToolWrench}) gets its full {@link IToolWrench#wrenchUsed} behaviour — including any
     * advancement it grants. A tag-only foreign wrench has no such callback, so we only swing the
     * arm (the universal "I used a tool" feedback) and deliberately do not award BuildCraft
     * progression to a non-BuildCraft item.
     */
    public static void wrenchUsed(Player player, InteractionHand hand, ItemStack stack, HitResult trace) {
        if (stack.getItem() instanceof IToolWrench wrench) {
            wrench.wrenchUsed(player, hand, stack, trace);
        } else {
            swing(player, hand);
        }
    }

    /** Calls {@link #wrenchUsed} on whichever hand holds the wrench. */
    public static void activateWrench(Player player, HitResult trace) {
        InteractionHand hand = getWrenchHand(player);
        if (hand != null) {
            wrenchUsed(player, hand, player.getItemInHand(hand), trace);
        }
    }

    /**
     * Swings {@code entity}'s arm for {@code hand}: the "I used something" feedback. Seen by the players
     * tracking the entity, not echoed back to the entity itself (the client plays its own swing). This is
     * the one-argument {@code LivingEntity#swing(hand)}; 26.3 removed it and wants the animation, which is
     * the held item's interact animation, as vanilla's own interaction swing picks it.
     */
    public static void swing(LivingEntity entity, InteractionHand hand) {
        //? if >=26.3 {
        entity.swing(hand, entity.getItemInHand(hand).getInteractAnimation(), false);
        //?} else {
        /*entity.swing(hand);
        *///?}
    }

    /**
     * Drops {@code stack} at {@code player} because it did not fit in their inventory (not thrown from the
     * hand). For server-side paths the client never runs, as every caller returns early on the logical
     * client. 26.3's {@code Player#drop} swings the arm and takes a {@code Prediction} saying whether the
     * client already did that itself; it did not here, so the swing goes to the dropping player too
     * ({@code SERVER_ONLY}, vanilla's choice for such paths).
     */
    public static void dropOverflow(Player player, ItemStack stack) {
        //? if >=26.3 {
        player.drop(stack, false, Prediction.SERVER_ONLY);
        //?} else {
        /*player.drop(stack, false);
        *///?}
    }
}
