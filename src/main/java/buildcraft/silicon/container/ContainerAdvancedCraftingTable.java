/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.container;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
//? if >=1.21.10 {
import net.minecraft.world.entity.player.StackedItemContents;
//?}
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.misc.CraftingUtil;

import buildcraft.silicon.BCSiliconMenuTypes;
import buildcraft.silicon.tile.TileAdvancedCraftingTable;

@SuppressWarnings("this-escape")
public class ContainerAdvancedCraftingTable extends ContainerBCTile<TileAdvancedCraftingTable> {

    private final List<Slot> blueprintSlots = new ArrayList<>();

    // Client-side constructor (from network)
    public ContainerAdvancedCraftingTable(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv.player, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerAdvancedCraftingTable(int containerId, Player player, TileAdvancedCraftingTable tile) {
        super(BCSiliconMenuTypes.ADVANCED_CRAFTING_TABLE.get(), containerId, player, tile);

        // Display slot showing current recipe result
        addSlot(new buildcraft.lib.gui.slot.SlotDisplay(i -> tile.resultClient, 0, 127, 33));

        // 5x3 material input slots
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) {
                addSlot(new SlotBase(tile.invMaterials, x + y * 5, 15 + x * 18, 85 + y * 18));
            }
        }

        // 3x3 result output slots
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new SlotOutput(tile.invResults, x + y * 3, 109 + x * 18, 85 + y * 18));
            }
        }

        // 3x3 phantom blueprint slots
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                Slot slot = new SlotPhantom(tile.invBlueprint, x + y * 3, 33 + x * 18, 16 + y * 18, false);
                addSlot(slot);
                blueprintSlots.add(slot);
            }
        }

        addFullPlayerInventory(8, 153);
    }

    // --- Recipe Book Support ---

    /** @return The 3x3 blueprint grid slots (used by the recipe book component). */
    public List<Slot> getInputGridSlots() {
        return blueprintSlots;
    }

    /** @return Grid width for the recipe book. */
    public int getGridWidth() {
        return 3;
    }

    /** @return Grid height for the recipe book. */
    public int getGridHeight() {
        return 3;
    }

    /** @return The display slot (result preview). */
    public Slot getResultSlot() {
        return this.slots.get(0); // First slot added is the display slot
    }

    //? if <1.21.10 {
    /*// 1.21.1-only RecipeBookMenu abstracts (modern's RecipeBookMenu lacks these); the base stubs them
    // grid-0, the table reports its real 3x3 blueprint grid + result slot. handlePlacement is overridden
    // to set the PHANTOM blueprint pattern via CraftingUtil (the JEI "+" path) instead of vanilla's
    // ServerPlaceRecipe, which would MOVE real items into the phantom grid and just delete them.
    @Override
    public int getResultSlotIndex() {
        return 0; // the display/result slot is the first slot added
    }

    @Override
    public int getSize() {
        return 9; // 3x3 blueprint grid
    }

    @Override
    public void handlePlacement(boolean useMaxItems, RecipeHolder<?> recipe,
            net.minecraft.server.level.ServerPlayer player) {
        if (recipe.value() instanceof CraftingRecipe craftingRecipe
                && player.level() instanceof ServerLevel serverLevel) {
            CraftingUtil.placeRecipeInBlueprint(craftingRecipe, tile.invBlueprint, serverLevel);
        }
    }*/
    //?}

    //? if >=1.21.10 {
    @Override
    public PostPlaceAction handlePlacement(boolean useMaxItems, boolean isCreative, RecipeHolder<?> recipe,
        ServerLevel level, Inventory playerInv) {
        // Only handle crafting recipes
        if (!(recipe.value() instanceof CraftingRecipe craftingRecipe)) {
            return PostPlaceAction.NOTHING;
        }

        // Delegate to CraftingUtil (in lib where AT is applied)
        CraftingUtil.placeRecipeInBlueprint(craftingRecipe, tile.invBlueprint, level);

        return PostPlaceAction.PLACE_GHOST_RECIPE;
    }
    //?} else {
    /*@Override
    public void handlePlacement(boolean useMaxItems, boolean isCreative, RecipeHolder<?> recipe,
        ServerLevel level, Inventory playerInv) {
        if (!(recipe.value() instanceof CraftingRecipe craftingRecipe)) {
            return;
        }
        CraftingUtil.placeRecipeInBlueprint(craftingRecipe, tile.invBlueprint, level);
    }*/
    //?}

    @Override
    //? if >=1.21.10 {
    public void fillCraftSlotsStackedContents(StackedItemContents contents) {
    //?} else {
    /*public void fillCraftSlotsStackedContents(net.minecraft.world.entity.player.StackedContents contents) {*/
    //?}
        // Report material inventory contents to the recipe book for craftability display
        for (int i = 0; i < tile.invMaterials.getSlots(); i++) {
            contents.accountStack(tile.invMaterials.getStackInSlot(i));
        }
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    private static TileAdvancedCraftingTable getTile(Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        var be = inv.player.level().getBlockEntity(pos);
        return be instanceof TileAdvancedCraftingTable t ? t : null;
    }
}
