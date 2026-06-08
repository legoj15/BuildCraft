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
//? if >=1.21.10 {
import net.minecraft.world.entity.player.StackedItemContents;
//?}
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.factory.BCFactoryMenuTypes;
import buildcraft.factory.tile.TileAutoWorkbenchItems;
import buildcraft.lib.gui.ContainerBCTile;
import buildcraft.lib.gui.slot.SlotBase;
import buildcraft.lib.gui.slot.SlotDisplay;
import buildcraft.lib.gui.slot.SlotOutput;
import buildcraft.lib.gui.slot.SlotPhantom;
import buildcraft.lib.misc.CraftingUtil;
import buildcraft.lib.net.PacketBufferBC;

@SuppressWarnings("this-escape")
public class ContainerAutoCraftItems extends ContainerBCTile<TileAutoWorkbenchItems> {

    private final List<Slot> blueprintSlots = new ArrayList<>();
    public final SlotBase[] materialSlots;

    /** Message id for the client→server "cycle output" button (above the base ids in ContainerBC_Neptune). */
    public static final int NET_CYCLE_OUTPUT = 104;
    /** Client-synced copies of the tile's match count + selected index (server fills these via DataSlots). */
    private int clientMatchCount;
    private int clientSelectedIndex;

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
        // Sync the conflicting-recipe count + selected index for the cycle-output button.
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override public int get() { return tile.getCraftingMatchCount(); }
            @Override public void set(int value) { clientMatchCount = value; }
        });
        addDataSlot(new net.minecraft.world.inventory.DataSlot() {
            @Override public int get() { return tile.getCraftingSelectedIndex(); }
            @Override public void set(int value) { clientSelectedIndex = value; }
        });

        addFullPlayerInventory(8, 115);
    }

    // --- Cycle Output ---

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        if (id == NET_CYCLE_OUTPUT && !isClient) {
            boolean next = buffer.readBoolean();
            tile.cycleCraftingOutput(next ? 1 : -1);
        } else {
            super.readMessage(id, buffer, isClient, ctx);
        }
    }

    /** Number of recipes the blueprint matches, synced from the server (&gt;1 shows the cycle button). */
    public int getSyncedMatchCount() {
        return clientMatchCount;
    }

    /** Index of the selected output among the matches, synced from the server. */
    public int getSyncedSelectedIndex() {
        return clientSelectedIndex;
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

    //? if <1.21.10 {
    /*// 1.21.1 RecipeBookMenu has these extra abstracts that modern's RecipeBookMenu doesn't. The base
    // ContainerBC_Neptune stubs them to a grid-0 no-op for non-crafting containers; the Auto Workbench
    // reports its real 3x3 blueprint grid + output slot so the recipe book lays out and fills correctly.
    @Override
    public int getResultSlotIndex() {
        return 0; // the output slot is the first slot added
    }

    @Override
    public int getSize() {
        return 9; // 3x3 blueprint grid
    }

    // Recipe-book click: vanilla RecipeBookMenu.handlePlacement defaults to ServerPlaceRecipe, which MOVES
    // real items from the player's inventory into the grid — wrong for our PHANTOM blueprint grid (it just
    // deletes them). Override to set the phantom pattern instead (the same CraftingUtil path the JEI "+"
    // button uses), consuming nothing and regardless of whether the player has the materials.
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
        if (!(recipe.value() instanceof CraftingRecipe craftingRecipe)) {
            return PostPlaceAction.NOTHING;
        }
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

