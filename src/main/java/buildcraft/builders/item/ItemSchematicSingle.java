/* Copyright (c) 2016 SpaceToad and the BuildCraft team
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package buildcraft.builders.item;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
//? if >=1.21.10 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;

import buildcraft.lib.inventory.InventoryWrapper;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.SoundUtil;
import buildcraft.lib.misc.StackUtil;

import buildcraft.builders.BCBuildersItems;
import buildcraft.builders.snapshot.SchematicBlockManager;
import buildcraft.core.PaperAdvancement;

/**
 * Single Block Schematic item — captures a single block's schematic data (right-click)
 * and can replay it (place the captured block) consuming inventory items.
 * <p>
 * In 1.12.2 this used damage values (0=clean, 1=used). In 1.21.11 there are two
 * separate registered items: schematic_single_clean and schematic_single_used.
 */
@SuppressWarnings("deprecation")
public class ItemSchematicSingle extends Item {
    public static final String NBT_KEY = "schematic";

    private final boolean used;

    public ItemSchematicSingle(Item.Properties properties, boolean used) {
        super(properties);
        this.used = used;
    }

    public boolean isUsed() {
        return used;
    }

    // region Right-click in air — sneak to clear
    @Override
    //? if >=1.21.10 {
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        return useImpl(world, player, hand);
    }
    //?} else {
    /*// 1.21.1: Item.use returns InteractionResultHolder<ItemStack>. Wrap the shared InteractionResult
    // logic (useImpl) with the held stack — InteractionResult.PASS/SUCCESS are version-neutral.
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        return new net.minecraft.world.InteractionResultHolder<>(useImpl(world, player, hand), player.getItemInHand(hand));
    }*/
    //?}

    private InteractionResult useImpl(Level world, Player player, InteractionHand hand) {
        if (world.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (used && player.isShiftKeyDown()) {
            // Clear the schematic → consume one used, give one clean.
            ItemStack stack = player.getItemInHand(hand);
            ItemStack clean = new ItemStack(BCBuildersItems.SCHEMATIC_SINGLE_CLEAN.get(), 1);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, clean));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    // endregion

    // region Right-click on block — capture or place
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        if (world.isClientSide()) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        InteractionHand hand = context.getHand();
        ItemStack stack = player.getItemInHand(hand);
        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();

        // Sneak+use on a used schematic → consume one used, give one clean.
        if (used && player.isShiftKeyDown()) {
            ItemStack clean = new ItemStack(BCBuildersItems.SCHEMATIC_SINGLE_CLEAN.get(), 1);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, clean));
            return InteractionResult.SUCCESS;
        }

        if (!used) {
            // === CAPTURE MODE (clean schematic) ===
            BlockState state = world.getBlockState(pos);
            ISchematicBlock schematicBlock = SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(
                world, pos, pos, state, state.getBlock()
            ));
            if (schematicBlock.isAir()) {
                return InteractionResult.FAIL;
            }
            // Build the captured "used" schematic, then consume one clean from the held
            // stack and deliver the used one — without ItemUtils this would void the rest
            // of the stack.
            ItemStack usedStack = new ItemStack(BCBuildersItems.SCHEMATIC_SINGLE_USED.get(), 1);
            CompoundTag itemData = new CompoundTag();
            itemData.put(NBT_KEY, SchematicBlockManager.writeToNBT(schematicBlock));
            NBTUtilBC.setItemData(usedStack, itemData);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, usedStack));
            AdvancementUtil.unlockAdvancement(player, PaperAdvancement.ID,
                PaperAdvancement.CAPTURE_WITH_SCHEMATIC);
            return InteractionResult.SUCCESS;
        } else {
            // === PLACE MODE (used schematic) ===
            BlockPos placePos = pos;
            BlockState clickedState = world.getBlockState(pos);
            boolean replaceable = clickedState.canBeReplaced();
            if (!replaceable) {
                placePos = placePos.relative(side);
            }
            BlockState placeState = world.getBlockState(placePos);
            if (!replaceable && !placeState.canBeReplaced()) {
                return InteractionResult.FAIL;
            }
            if (replaceable && !world.isEmptyBlock(placePos)) {
                world.removeBlock(placePos, false);
            }
            try {
                ISchematicBlock schematicBlock = getSchematic(stack);
                if (schematicBlock != null) {
                    if (!schematicBlock.isBuilt(world, placePos) && schematicBlock.canBuild(world, placePos)) {
                        List<FluidStack> requiredFluids = schematicBlock.computeRequiredFluids();
                        List<ItemStack> requiredItems = schematicBlock.computeRequiredItems();
                        if (requiredFluids.isEmpty()) {
                            InventoryWrapper itemTransactor = new InventoryWrapper(player.getInventory());
                            if (StackUtil.mergeSameItems(requiredItems).stream().noneMatch(s ->
                                itemTransactor.extract(
                                    extracted -> StackUtil.canMerge(s, extracted),
                                    s.getCount(),
                                    s.getCount(),
                                    true
                                ).isEmpty()
                            )) {
                                if (schematicBlock.build(world, placePos)) {
                                    StackUtil.mergeSameItems(requiredItems).forEach(s ->
                                        itemTransactor.extract(
                                            extracted -> StackUtil.canMerge(s, extracted),
                                            s.getCount(),
                                            s.getCount(),
                                            false
                                        )
                                    );
                                    SoundUtil.playBlockPlace(world, placePos);
                                    player.swing(hand);
                                    return InteractionResult.SUCCESS;
                                }
                            } else {
                                buildcraft.lib.misc.MessageUtil.sendOverlayMessage(player,
                                    Component.literal(
                                        "Not enough items. Total needed: " +
                                            StackUtil.mergeSameItems(requiredItems).stream()
                                                .map(s -> s.getHoverName().getString() + " x " + s.getCount())
                                                .collect(Collectors.joining(", "))
                                    )
                                );
                            }
                        } else {
                            buildcraft.lib.misc.MessageUtil.sendOverlayMessage(player,
                                Component.literal("Schematic requires fluids")
                            );
                        }
                    }
                }
            } catch (InvalidInputDataException e) {
                buildcraft.lib.misc.MessageUtil.sendOverlayMessage(player,
                    Component.literal("Invalid schematic: " + e.getMessage())
                );
                BCLog.logger.warn("[builders.schematic] Player tried to use an invalid schematic", e);
            }
            return InteractionResult.FAIL;
        }
    }
    // endregion

    // region Static helpers
    public static ISchematicBlock getSchematic(@Nonnull ItemStack stack) throws InvalidInputDataException {
        if (stack.getItem() instanceof ItemSchematicSingle) {
            CompoundTag itemData = NBTUtilBC.getItemData(stack);
            if (itemData.contains(NBT_KEY)) {
                return SchematicBlockManager.readFromNBT(NBTUtilBC.getCompound(itemData, NBT_KEY));
            }
        }
        return null;
    }

    public static ISchematicBlock getSchematicSafe(@Nonnull ItemStack stack) {
        try {
            return getSchematic(stack);
        } catch (InvalidInputDataException e) {
            BCLog.logger.warn("Invalid schematic " + e.getMessage());
            return null;
        }
    }
    // endregion

    // region Tooltip — captured-block name
    /**
     * Show the captured block's display name under the main tooltip title for "used"
     * variants. 1.12.2 never did this, which made the identical-looking items confusing
     * to tell apart without hovering on an actual block. The separate 3D preview panel is
     * drawn from {@code SchematicSingleTooltipOverlay}, matching the blueprint tooltip's
     * handler pattern.
     */
    @Override
    //? if >=1.21.10 {
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
    //?} else {
    /*// 1.21.1: appendHoverText has no TooltipDisplay and takes List<Component>; adapt to the shared
    // Consumer-based body below via tooltipList::add.
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                java.util.List<Component> tooltipList, TooltipFlag flag) {
        Consumer<Component> tooltip = tooltipList::add;
        super.appendHoverText(stack, context, tooltipList, flag);*/
    //?}
        if (!used) {
            tooltip.accept(Component.translatable("item.blueprint.blank").withStyle(ChatFormatting.GRAY));
            tooltip.accept(
                Component.translatable("item.schematic_single.use_hint", Component.keybind("key.use"))
                    .withStyle(ChatFormatting.GRAY)
            );
            return;
        }
        ISchematicBlock schematic = getSchematicSafe(stack);
        if (schematic == null) {
            return;
        }
        BlockState state = schematic.getBlockStateForRender();
        if (state == null) {
            return;
        }
        tooltip.accept(state.getBlock().getName().copy().withStyle(ChatFormatting.GRAY));
    }
    // endregion
}
