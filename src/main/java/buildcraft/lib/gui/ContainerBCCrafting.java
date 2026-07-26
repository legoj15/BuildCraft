/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.gui;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
//? if >=1.21.10 {
import net.minecraft.world.entity.player.StackedItemContents;
//?} else {
/*import net.minecraft.world.entity.player.StackedContents;*/
//?}
//? if >=26.1 {
import net.minecraft.world.inventory.ContainerInput;
//?} else {
/*import net.minecraft.world.inventory.ClickType;*/
//?}
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.AbstractBCBlockEntity;

/**
 * The tile-backed BuildCraft menu base for the two tables that genuinely use vanilla's recipe book —
 * the Auto Workbench and the Advanced Crafting Table (both drive a real {@code RecipeBookComponent}).
 * It carries the recipe-book surface, so {@link ContainerBC_Neptune} — the base for the other ~23
 * menus — no longer has to extend {@code RecipeBookMenu} and drag a version-forked 9-abstract-method
 * stub block onto every menu as dead inherited code.
 *
 * <p>Because Java is single-inheritance and this must extend {@code RecipeBookMenu}, it cannot also
 * extend {@link ContainerBCTile}; the tile-backing (the nullable {@code tile} field + open/close
 * tracking + reach-checked {@code stillValid}) and the BC machinery ({@link BCContainerSupport}) are
 * therefore replicated here rather than inherited. Both are thin; the actual logic lives once (in
 * {@link AbstractBCBlockEntity#canInteractWith} and {@code BCContainerSupport}).
 */
//? if >=1.21.10 {
@SuppressWarnings({ "this-escape", "unchecked" })
public abstract class ContainerBCCrafting<T extends AbstractBCBlockEntity> extends RecipeBookMenu implements BCContainer {
//?} else {
/*@SuppressWarnings({ "this-escape", "unchecked" })
public abstract class ContainerBCCrafting<T extends AbstractBCBlockEntity>
        extends RecipeBookMenu<net.minecraft.world.item.crafting.CraftingInput, CraftingRecipe> implements BCContainer {*/
//?}

    public final Player player;
    @Nullable
    public final T tile;
    private final BCContainerSupport support;

    protected ContainerBCCrafting(MenuType<?> menuType, int containerId, Player player, @Nullable T tile) {
        super(menuType, containerId);
        this.player = player;
        this.tile = tile;
        this.support = new BCContainerSupport(this, player);
        if (tile != null && tile.getLevel() != null && !tile.getLevel().isClientSide()) {
            tile.onPlayerOpen(player);
        }
    }

    @Override
    public BCContainerSupport bcSupport() {
        return support;
    }

    protected void addFullPlayerInventory(int startX, int startY) {
        support.addFullPlayerInventory(startX, startY, player.getInventory(), this::addSlot);
    }

    protected void addFullPlayerInventory(int startX, int startY, Inventory inv) {
        support.addFullPlayerInventory(startX, startY, inv, this::addSlot);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (tile != null) {
            tile.onPlayerClose(player);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return tile != null && tile.canInteractWith(player);
    }

    // --- Slot handling ---

    @Override
    //? if >=26.1 {
    public void clicked(int slotId, int dragType, ContainerInput containerInput, Player player) {
    //?} else {
    /*public void clicked(int slotId, int dragType, ClickType containerInput, Player player) {*/
    //?}
        if (support.handlePhantomClick(slotId)) {
            return;
        }
        super.clicked(slotId, dragType, containerInput, player);
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        return support.quickMoveStack(index, this::moveItemStackTo);
    }

    // --- Networking ---
    // No sendWidgetData bridge: a Widget_Neptune requires a ContainerBC_Neptune, so none can bind to a
    // crafting table. These menus sync via vanilla DataSlots + the recipe book, not the widget channel.

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        if (support.readMessage(id, buffer, isClient, ctx)) {
            return;
        }
        if (id == ContainerBC_Neptune.NET_JEI_RECIPE_TRANSFER && !isClient) {
            // Server-side: JEI requested recipe placement into the blueprint phantom grid. Look up the
            // recipe by resource location and delegate to handlePlacement() — which the concrete tables
            // override to set the phantom pattern (the base stub below is a no-op).
            Identifier recipeId = Identifier.parse(buffer.readUtf());
            if (player.level() instanceof ServerLevel serverLevel) {
                //? if >=1.21.10 {
                net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> key =
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE, recipeId);
                Optional<RecipeHolder<CraftingRecipe>> holder = serverLevel.recipeAccess()
                        .byKey(key)
                //?} else {
                /*Optional<RecipeHolder<CraftingRecipe>> holder = serverLevel.getRecipeManager()
                        .byKey(recipeId)*/
                //?}
                        .filter(r -> r.value() instanceof CraftingRecipe)
                        .map(r -> (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) r);
                holder.ifPresent(recipe -> handlePlacement(
                        false, player.isCreative(), recipe,
                        serverLevel, player.getInventory()));
            }
        }
    }

    // --- Recipe book surface (default stubs; the concrete tables override the ones they use) ---
    // The version-forked block that used to live on ContainerBC_Neptune and get inherited (dead) by
    // every menu. Modern's RecipeBookMenu has 3 abstract methods; 1.21.1's RecipeBookMenu<I,R> has 9.

    //? if >=1.21.10 {
    @Override
    public PostPlaceAction handlePlacement(boolean useMaxItems, boolean isCreative, RecipeHolder<?> recipe,
        ServerLevel level, Inventory playerInv) {
        return PostPlaceAction.NOTHING;
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedItemContents contents) {
        // No-op by default
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }
    //?} else {
    /*// 1.21.1: RecipeBookMenu<I,R> has NINE abstract methods (vs modern's 3). Grid-0 no-op defaults so
    // a crafting container compiles; the concrete tables override the relevant ones with their real 3x3
    // grid. NOTE: this 5-arg handlePlacement is NOT an @Override on 1.21.1 — vanilla's 1.21.1
    // handlePlacement is 3-arg (boolean, RecipeHolder, ServerPlayer); this overload feeds the JEI
    // transfer path above and its result is unused, so it returns void.
    public void handlePlacement(boolean useMaxItems, boolean isCreative, RecipeHolder<?> recipe,
        ServerLevel level, Inventory playerInv) {
    }

    @Override
    public void fillCraftSlotsStackedContents(net.minecraft.world.entity.player.StackedContents contents) {
        // No-op by default
    }

    @Override
    public void clearCraftingContent() {
    }

    @Override
    public boolean recipeMatches(RecipeHolder<CraftingRecipe> recipe) {
        return false;
    }

    @Override
    public int getResultSlotIndex() {
        return -1;
    }

    @Override
    public int getGridWidth() {
        return 0;
    }

    @Override
    public int getGridHeight() {
        return 0;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    public boolean shouldMoveToInventory(int slotIndex) {
        return false;
    }*/
    //?}
}
