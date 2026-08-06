/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
//? if >=1.21.10 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.robotics.BCRoboticsItems;

/** The standalone board item — the thing a programming table (Ph7) eventually turns into a robot. It carries no
 *  charge; the board id rides in {@code CUSTOM_DATA} exactly as {@link ItemRobot} carries board+charge, and
 *  every accessor reads through {@code NbtApiUtil}-style guards. Ph4 ships no board recipe (D5): the item is
 *  reachable through the creative tab and game tests only. */
// Item.appendHoverText carries Mojang's "override, don't call" @Deprecated marker on >=1.21.10 — the same
// suppression every other BC item with a tooltip carries.
@SuppressWarnings("deprecation")
public class ItemRedstoneBoard extends Item {

    /** The board id sub-compound, keyed as 7.1.x did. */
    public static final String TAG_BOARD = "board";

    /** The board sub-compound's own id key, written by {@code RedstoneBoardNBT.createBoard}. */
    private static final String TAG_BOARD_ID = "id";

    public ItemRedstoneBoard(Item.Properties properties) {
        super(properties);
    }

    /** Builds a board stack for the given board. {@code boardNBT.createBoard} stamps the id, so the blob is
     *  {@code {board:{id: <id>}}}. */
    public static ItemStack createStack(RedstoneBoardNBT<?> boardNBT) {
        ItemStack stack = new ItemStack(BCRoboticsItems.REDSTONE_BOARD.get());
        CompoundTag blob = new CompoundTag();
        if (boardNBT != null) {
            CompoundTag board = new CompoundTag();
            boardNBT.createBoard(board);
            blob.put(TAG_BOARD, board);
        }
        writeBlob(stack, blob);
        return stack;
    }

    /** Resolves the stack's board through the registry, falling back to the empty board for an absent or
     *  unregistered id (7.1.x behaviour). */
    public static RedstoneBoardNBT<?> getBoardNBT(ItemStack stack) {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        if (registry == null) {
            return null;
        }
        CompoundTag blob = readBlob(stack);
        CompoundTag board = NbtApiUtil.getCompound(blob, TAG_BOARD);
        if (!board.contains(TAG_BOARD_ID)) {
            // A bare stack (no blob, or a corrupt one) reads as the empty board, as upstream guaranteed.
            board = new CompoundTag();
            registry.getEmptyRobotBoard().createBoard(board);
        }
        return registry.getRedstoneBoard(board);
    }

    /** Never null: a stack with no {@code CUSTOM_DATA} yields a fresh empty compound. */
    private static CompoundTag readBlob(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static void writeBlob(ItemStack stack, CompoundTag blob) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, existing -> CustomData.of(blob));
    }

    @Override
    //? if >=1.21.10 {
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
    //?} else {
    /*public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            java.util.List<Component> tooltipList, TooltipFlag flag) {
        Consumer<Component> tooltip = tooltipList::add;
        super.appendHoverText(stack, context, tooltipList, flag);*/
    //?}
        RedstoneBoardNBT<?> board = getBoardNBT(stack);
        if (board != null) {
            List<String> lines = new java.util.ArrayList<>();
            // The board's addInformation writes into a plain string list (the API predates components); each
            // line is pushed as a Component so the tooltip is localised like any other BC text. The board
            // implementations do not consult the player, so null is an honest stand-in here.
            board.addInformation(stack, null, lines, flag.isAdvanced());
            for (String line : lines) {
                tooltip.accept(Component.literal(line));
            }
        }
    }
}
