/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.core.item;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import buildcraft.api.blocks.CustomPaintHelper;
import buildcraft.core.BCCore;
import buildcraft.lib.misc.ColourUtil;
import buildcraft.lib.misc.ParticleUtil;
import buildcraft.lib.misc.SoundUtil;
import buildcraft.lib.misc.VecUtil;

@SuppressWarnings("deprecation")
public class ItemPaintbrush_BC8 extends Item {
    private static final int MAX_USES = 64;

    public ItemPaintbrush_BC8(Item.Properties properties) {
        super(properties);
    }

    // --- Brush data via typed data components ---

    @Nullable
    private static DyeColor getColour(ItemStack stack) {
        return stack.get(BCCore.BRUSH_COLOR.get());
    }

    private static int getUsesLeft(ItemStack stack) {
        Integer uses = stack.get(BCCore.BRUSH_USES.get());
        return uses != null ? uses : 0;
    }

    private static void setBrushData(ItemStack stack, @Nullable DyeColor colour, int usesLeft) {
        if (colour == null || usesLeft <= 0) {
            // Clean brush — remove all brush components
            stack.remove(BCCore.BRUSH_COLOR.get());
            stack.remove(BCCore.BRUSH_USES.get());
            stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            return;
        }
        stack.set(BCCore.BRUSH_COLOR.get(), colour);
        stack.set(BCCore.BRUSH_USES.get(), usesLeft);
        // Set CUSTOM_MODEL_DATA for the range_dispatch item model selector.
        // Values 1-16 map to DyeColor ordinals + 1 (0 = clean/fallback).
        // In 1.21.4+, CustomModelData takes (floats, flags, strings, colors).
        //? if >=1.21.10 {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                java.util.List.of((float) (colour.ordinal() + 1)),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        ));
        //?} else {
        /*// 1.21.1: CustomModelData is the single-int record.
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(colour.ordinal() + 1));*/
        //?}
    }

    /** Create a pre-colored paintbrush stack (for creative tab population). */
    public static ItemStack createColoredStack(Item paintbrushItem, @Nullable DyeColor colour) {
        ItemStack stack = new ItemStack(paintbrushItem);
        if (colour != null) {
            setBrushData(stack, colour, MAX_USES);
        }
        return stack;
    }

    /** A copy of {@code brush} with {@code count} uses consumed, reverting to a clean brush when the
     *  charge runs out — the same per-block cost as in-world painting ({@link #useOn}). Used by the
     *  pipe-painting crafting recipe so the brush's dye efficiency carries into crafting. */
    public static ItemStack withUsesConsumed(ItemStack brush, int count) {
        ItemStack result = brush.copy();
        int usesLeft = getUsesLeft(brush) - count;
        if (usesLeft > 0) {
            setBrushData(result, getColour(brush), usesLeft);
        } else {
            setBrushData(result, null, 0);
        }
        return result;
    }

    // --- Item overrides ---

    @Override
    public Component getName(ItemStack stack) {
        DyeColor colour = getColour(stack);
        if (colour != null) {
            String colourName = ColourUtil.getTextFullTooltip(colour);
            return Component.empty()
                .append(Component.literal(colourName + " "))
                .append(super.getName(stack));
        }
        return super.getName(stack);
    }

    @Override
    //? if >=1.21.10 {
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
    //?} else {
    /*// 1.21.1: appendHoverText has no TooltipDisplay and takes List<Component>; adapt to the shared
    // Consumer-based body below via tooltipList::add.
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            java.util.List<Component> tooltipList, net.minecraft.world.item.TooltipFlag flag) {
        java.util.function.Consumer<Component> tooltip = tooltipList::add;
        super.appendHoverText(stack, context, tooltipList, flag);*/
    //?}
        if (getColour(stack) == null) {
            tooltip.accept(Component.translatable("tip.item.paintbrush.clean").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        DyeColor colour = getColour(stack);
        return colour != null && getUsesLeft(stack) < MAX_USES;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int usesLeft = getUsesLeft(stack);
        return Math.round((usesLeft / (float) MAX_USES) * 13.0f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        DyeColor colour = getColour(stack);
        if (colour != null) {
            return colour.getTextureDiffuseColor();
        }
        return super.getBarColor(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        DyeColor colour = getColour(stack);
        int usesLeft = getUsesLeft(stack);

        if (colour != null && usesLeft <= 0) {
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();
        BlockState state = level.getBlockState(pos);
        Vec3 hitPos = context.getClickLocation();

        InteractionResult result = CustomPaintHelper.INSTANCE.attemptPaintBlock(level, pos, state, hitPos, side, colour);

        if (result == InteractionResult.SUCCESS) {
            if (!level.isClientSide()) {
                ParticleUtil.showChangeColour(level, hitPos, colour);
                SoundUtil.playChangeColour(level, pos, colour);

                if (!player.isCreative()) {
                    usesLeft--;
                }

                if (usesLeft <= 0) {
                    colour = null;
                    usesLeft = 0;
                }
                setBrushData(stack, colour, usesLeft);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    // --- Public accessors ---

    public Brush getBrushFromStack(ItemStack stack) {
        return new Brush(getColour(stack), getUsesLeft(stack));
    }

    /** Simple data holder for brush state. */
    public static class Brush {
        @Nullable
        public final DyeColor colour;
        public final int usesLeft;

        public Brush(@Nullable DyeColor colour, int usesLeft) {
            this.colour = colour;
            this.usesLeft = usesLeft;
        }

        @Override
        public String toString() {
            return "[" + usesLeft + " of " + (colour == null ? "nothing" : colour.getName()) + "]";
        }
    }
}
