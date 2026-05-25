/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.factory.container;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.misc.CraftingUtil;

@SuppressWarnings("this-escape")
public class ContainerAutoCraftItems extends ContainerBCTile<TileAutoWorkbenchItems> {

    private final List<Slot> blueprintSlots = new ArrayList<>();
    public final SlotBase[] materialSlots;

    // Client-side constructor (from network)
    public ContainerAutoCraftItems(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getTile(playerInv, buf));
    }

    // Server-side constructor
    public ContainerAutoCraftItems(int containerId, Inventory playerInv, TileAutoWorkbenchItems tile) {
        super(BCFactoryMenuTypes.AUTO_WORKBENCH_ITEMS.get(), containerId, playerInv.player, tile);

        // Result output slot at (124, 35) — matches 1.12.2
        addSlot(new SlotOutput(tile.invResult, 0, 124, 35));

        // Blueprint phantom slots (3x3 grid) — top-left at (30, 17)
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                Slot slot = new SlotPhantom(tile.invBlueprint, x + y * 3,
                        30 + x * 18, 17 + y * 18, false);
                addSlot(slot);
                blueprintSlots.add(slot);
            }
        }

        // Material filter phantom slots (hidden off-screen, synced for GUI overlay)
        for (int x = 0; x < 9; x++) {
            addSlot(new SlotPhantom(tile.invMaterialFilter, x, -1000000, -1000000));
        }

        // Materials slots — single row of 9 at (8 + x*18, 84)
        materialSlots = new SlotBase[9];
        for (int x = 0; x < 9; x++) {
            materialSlots[x] = new SlotBase(tile.invMaterials, x, 8 + x * 18, 84);
            addSlot(materialSlots[x]);
        }

        // Recipe result display slot at (93, 27) — matches 1.12.2
        addSlot(new SlotDisplay(i -> tile.resultClient, 0, 93, 27));

        // Sync powerStored to client for progress bar (long → two int data slots)
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override public int get() { return (int) (tile.getPowerStored() & 0xFFFFFFFFL); }
            @Override public void set(int value) {
                long current = tile.getPowerStored();
                tile.setPowerStored((current & 0xFFFFFFFF00000000L) | (value & 0xFFFFFFFFL));
            }
        });
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override public int get() { return (int) (tile.getPowerStored() >>> 32); }
            @Override public void set(int value) {
                long current = tile.getPowerStored();
                tile.setPowerStored((current & 0x00000000FFFFFFFFL) | ((long) value << 32));
            }
        });

        // Player inventory at (8, 115)
        addFullPlayerInventory(8, 115);
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

    /** @return The result output slot. */
    public Slot getResultSlot() {
        return this.slots.get(0); // First slot added is the output slot
    }

    @Override
    public PostPlaceAction handlePlacement(boolean useMaxItems, boolean isCreative, RecipeHolder<?> recipe,
        ServerLevel level, Inventory playerInv) {
        if (!(recipe.value() instanceof CraftingRecipe craftingRecipe)) {
            return PostPlaceAction.NOTHING;
        }
        CraftingUtil.placeRecipeInBlueprint(craftingRecipe, tile.invBlueprint);
        return PostPlaceAction.PLACE_GHOST_RECIPE;
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedItemContents contents) {
        for (int i = 0; i < tile.invMaterials.getSlots(); i++) {
            contents.accountStack(tile.invMaterials.getStackInSlot(i));
        }
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    private static TileAutoWorkbenchItems getTile(Inventory playerInv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInv.player.level() != null) {
            var be = playerInv.player.level().getBlockEntity(pos);
            if (be instanceof TileAutoWorkbenchItems workbench) {
                return workbench;
            }
        }
        return null;
    }
}

