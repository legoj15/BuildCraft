/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import buildcraft.api.blocks.CustomRotationHelper;
import buildcraft.api.blocks.ICustomRotationHandler;
import buildcraft.api.tools.IToolWrench;

import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.SoundUtil;

/**
 * The basic wrench, and a real tool: it has durability, can be enchanted and mended, and hits like a light tool.
 * Every {@link #wrenchUsed} costs one use; the {@link WrenchTier} says what else it is made of.
 */
public class ItemWrench_Neptune extends Item implements IToolWrench {
    private static final Identifier ADVANCEMENT = Identifier.parse("buildcraftunofficial:wrenched");

    //? if <1.21.10 {
    /*private final WrenchTier tier;*/
    //?}

    public ItemWrench_Neptune(Item.Properties properties) {
        this(properties, WrenchTier.IRON);
    }

    protected ItemWrench_Neptune(Item.Properties properties, WrenchTier tier) {
        super(tier.apply(properties));
        //? if <1.21.10 {
        /*this.tier = tier;*/
        //?}
    }

    @Override
    public boolean canWrench(Player player, net.minecraft.world.InteractionHand hand, ItemStack wrench,
            HitResult rayTrace) {
        return true;
    }

    /**
     * The durability hook {@link IToolWrench#wrenchUsed} documents: one use per successful wrench action. The swing
     * comes first because breaking the wrench empties the stack the swing animation is read from. A creative
     * player's wrench does not wear (the stack's own rule), and nothing may read {@code wrench} afterwards: it can be
     * empty.
     */
    @Override
    public void wrenchUsed(Player player, net.minecraft.world.InteractionHand hand, ItemStack wrench,
            HitResult rayTrace) {
        AdvancementUtil.unlockAdvancement(player, ADVANCEMENT);
        EntityUtil.swing(player, hand);
        wrench.hurtAndBreak(1, player, hand == net.minecraft.world.InteractionHand.MAIN_HAND
            ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
    }

    //? if <1.21.10 {
    /*// 1.21.1 has no enchantable/repairable/weapon components: the item answers those questions itself.
    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return tier.enchantmentValue();
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairWith) {
        return tier.repairs(repairWith) || super.isValidRepairItem(stack, repairWith);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target,
            net.minecraft.world.entity.LivingEntity attacker) {
        return true;
    }

    // One use per hit on a mob (a vanilla tool costs two); the 1.21.10+ twin is the weapon component.
    @Override
    public void postHurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target,
            net.minecraft.world.entity.LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }*/
    //?}

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, net.minecraft.world.level.LevelReader level,
            net.minecraft.core.BlockPos pos, Player player) {
        // true = sneak-clicks pass through to the block's useItemOn. Required so
        // engine/dynamo blocks can intercept crouch+wrench and open the GUI instead
        // of being short-circuited to the wrench's own rotation path.
        return true;
    }

    /**
     * Crouch+wrench on a vanilla block invokes rotation; non-crouch+wrench falls
     * through to the block's normal interaction (trapdoor opens, lever flips,
     * furnace opens its menu, …). This matches the 1.12.2 UX where the player had
     * to crouch to "switch the wrench on" for vanilla blocks.
     * <p>
     * Why this lives in {@code onItemUseFirst} rather than relying on sneak naturally
     * suppressing the block: with {@code doesSneakBypassUse=true} (needed so BC
     * engines/dynamos can intercept crouch+wrench to open their GUI), the
     * {@code suppressUsingBlock} guard in {@code ServerPlayerGameMode.useItemOn} only
     * fires when both hands report sneak-bypass. {@link ItemStack#doesSneakBypassUse}
     * short-circuits to {@code true} for an empty stack, so a player crouching with
     * the wrench in main + nothing in offhand has both hands "bypassing" — and the
     * block wins every time. Running rotation here, gated on the crouch state,
     * sidesteps that.
     * <p>
     * BC machines (engine, distiller, dynamo, …) implement {@link ICustomRotationHandler}
     * directly and keep their existing UX through {@link #useOn} and their own
     * {@code useItemOn}: non-crouch wrench rotates, crouch wrench opens the GUI.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        Level world = context.getLevel();
        net.minecraft.core.BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof ICustomRotationHandler) {
            return InteractionResult.PASS;
        }

        net.minecraft.core.Direction side = context.getClickedFace();
        InteractionResult result = CustomRotationHelper.INSTANCE.attemptRotateBlock(world, pos, state, side);
        if (result == InteractionResult.PASS) {
            return InteractionResult.PASS;
        }

        if (result == InteractionResult.SUCCESS) {
            BlockHitResult hit = new BlockHitResult(context.getClickLocation(), side, pos, context.isInside());
            wrenchUsed(player, context.getHand(), stack, hit);
        }
        SoundUtil.playSlideSound(world, pos, state, result);
        return result;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        net.minecraft.core.BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        net.minecraft.world.InteractionHand hand = context.getHand();
        net.minecraft.core.Direction side = context.getClickedFace();

        // if (world.isClientSide) {
        // return InteractionResult.PASS;
        // }

        BlockState state = world.getBlockState(pos);
        // state = state.getActualState(world, pos); // Not relevant in 1.21

        InteractionResult result = CustomRotationHelper.INSTANCE.attemptRotateBlock(world, pos, state, side);

        if (result == InteractionResult.SUCCESS && player != null) {
            BlockHitResult hitResult = new BlockHitResult(context.getClickLocation(), side, pos, context.isInside());
            wrenchUsed(player, hand, context.getItemInHand(), hitResult);
        }

        SoundUtil.playSlideSound(world, pos, state, result);
        return result;
    }
}
