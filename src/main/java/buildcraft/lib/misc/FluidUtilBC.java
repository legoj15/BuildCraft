/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?} else {
/*import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;*/
//?}

import net.minecraft.world.level.material.Fluids;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.lib.fluid.BCFluidTank;
import buildcraft.lib.tile.item.ItemHandlerSimple;

public class FluidUtilBC {

    /**
     * Returns whether the given fluid should be rendered with translucent
     * blending (e.g. vanilla water) rather than opaque cutout.
     *
     * <p>In 1.12.2, each BC fluid had its own opaque procedurally-generated
     * texture. In 1.21.11, all BC energy fluids reuse vanilla
     * {@code water_still / water_flow} textures tinted with a colour, but
     * those textures have semi-transparent pixels. Without this check,
     * oil, fuel, etc. would all inherit water's translucency.
     *
     * <p>The heuristic is simple: only vanilla water (still or flowing)
     * is expected to be translucent. Every other fluid — including BC
     * fluids that happen to reuse water's texture as a tint base — uses
     * cutout rendering so the texture alpha is clamped to binary.
     */
    public static boolean shouldRenderTranslucent(Fluid fluid) {
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    /** @see #shouldRenderTranslucent(Fluid) */
    public static boolean shouldRenderTranslucent(FluidStack stack) {
        return !stack.isEmpty() && shouldRenderTranslucent(stack.getFluid());
    }

    public static net.minecraft.resources.Identifier getFluidTexture(FluidStack stack) {
        if (stack.isEmpty() || stack.getFluid() == null || stack.getFluid().getFluidType() == null) return net.minecraft.resources.Identifier.withDefaultNamespace("block/water_still");
        Fluid fluid = stack.getFluid();
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            return net.minecraft.resources.Identifier.withDefaultNamespace("block/lava_still");
        }

        // Check if this is a BuildCraft energy fluid — use the recolored sprite ID.
        // Recolored sprites are named: <baseName>_heat_<heat>_still
        // The fluid type registry path is: "<baseName>" for heat 0, "<baseName>_heat_1" for heat 1, etc.
        // So the recolored sprite name is always: "<regPath>_heat_0_still" or "<regPath>_still" 
        // depending on whether heat suffix is already present.
        net.minecraft.resources.Identifier fluidId = net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES.getKey(fluid.getFluidType());
        if (fluidId != null && fluidId.getNamespace().equals("buildcraftunofficial")) {
            String path = fluidId.getPath();
            // Determine if heat suffix is already in the path
            String spriteName;
            if (path.contains("_heat_")) {
                // e.g., "oil_heat_1" -> "oil_heat_1_still"
                spriteName = path + "_still";
            } else {
                // e.g., "oil" -> "oil_heat_0_still"
                spriteName = path + "_heat_0_still";
            }
            return net.minecraft.resources.Identifier.fromNamespaceAndPath("buildcraftunofficial", "block/fluids/" + spriteName);
        }
        if (fluidId != null) {
            return net.minecraft.resources.Identifier.parse(fluidId.getNamespace() + ":block/" + fluidId.getPath() + "_still");
        }

        return net.minecraft.resources.Identifier.withDefaultNamespace("block/water_still"); // Default fallback
    }

    /**
     * Resolves the tint color of the fluid dynamically.
     */
    public static int getFluidColor(FluidStack stack) {
        if (stack.isEmpty() || stack.getFluid() == null || stack.getFluid().getFluidType() == null) return 0xFFFFFFFF;
        Fluid fluid = stack.getFluid();
        
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            return 0xFFFFFFFF; // Lava evaluates intrinsically without tinting
        }
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            return 0xFF3F76E4; // Base NeoForge water tint fallback
        }

        // BC energy fluids have colors baked into their recolored sprites — no tint needed
        net.minecraft.resources.Identifier fluidId = net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES.getKey(fluid.getFluidType());
        if (fluidId != null && fluidId.getNamespace().equals("buildcraftunofficial")) {
            return 0xFFFFFFFF;
        }

        //? if >=26.1 {
        try {
            net.minecraft.client.renderer.block.FluidModel model = net.minecraft.client.Minecraft.getInstance()
                    .getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            if (model != null && model.fluidTintSource() != null) {
                return model.fluidTintSource().color(fluid.defaultFluidState());
            }
        } catch (Exception e) {}

        return 0xFFFFFFFF;
        //?} else {
        /*// 1.21.11 has no FluidModel/getFluidStateModelSet; tint comes from the client fluid extension.
        return net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.defaultFluidState()).getTintColor();*/
        //?}
    }

    /**
     * Pushes fluid from the given FluidStacksResourceHandler to all adjacent fluid-capable blocks.
     * Uses NeoForge 1.21.11's Capabilities.Fluid.BLOCK with ResourceHandler API.
     */
    //? if >=1.21.10 {
    public static void pushFluidToNeighbors(Level level, BlockPos pos, FluidStacksResourceHandler tank) {
        if (tank.getAmountAsLong(0) <= 0) return;
        for (Direction dir : Direction.values()) {
            if (tank.getAmountAsLong(0) <= 0) break;
            BlockPos neighborPos = pos.relative(dir);
            ResourceHandler<FluidResource> neighbor = level.getCapability(
                    Capabilities.Fluid.BLOCK, neighborPos, dir.getOpposite());
            if (neighbor == null) continue;

            FluidResource resource = tank.getResource(0);
            if (resource.isEmpty()) break;

            int amountToTry = (int) Math.min(tank.getAmountAsLong(0), 1000);

            try (Transaction tx = Transaction.openRoot()) {
                int accepted = ResourceHandlerUtil.move(
                        tank, neighbor, r -> true, amountToTry, tx);
                if (accepted > 0) {
                    tx.commit();
                }
            }
        }
    }
    //?} else {
    /*public static void pushFluidToNeighbors(Level level, BlockPos pos, BCFluidTank tank) {
        if (tank.getAmountMb(0) <= 0) return;
        for (Direction dir : Direction.values()) {
            if (tank.getAmountMb(0) <= 0) break;
            BlockPos neighborPos = pos.relative(dir);
            IFluidHandler neighbor = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, neighborPos, dir.getOpposite());
            if (neighbor == null) continue;
            if (tank.isTankEmpty(0)) break;
            int amountToTry = Math.min(tank.getAmountMb(0), 1000);
            FluidUtil.tryFluidTransfer(neighbor, tank, amountToTry, true);
        }
    }*/
    //?}

    public static List<FluidStack> mergeSameFluids(List<FluidStack> fluids) {
        List<FluidStack> stacks = new ArrayList<>();
        fluids.forEach(toAdd -> {
            boolean found = false;
            for (FluidStack stack : stacks) {
                if (FluidStack.isSameFluidSameComponents(stack, toAdd)) {
                    stack.grow(toAdd.getAmount());
                    found = true;
                }
            }
            if (!found) {
                stacks.add(toAdd.copy());
            }
        });
        return stacks;
    }

    public static boolean areFluidStackEqual(FluidStack a, FluidStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return FluidStack.isSameFluidSameComponents(a, b) && a.getAmount() == b.getAmount();
    }

    public static boolean areFluidsEqual(Fluid a, Fluid b) {
        if (a == null || b == null) {
            return a == b;
        }
        // Mojang split source/flowing into separate Fluid instances in 1.13, so
        // strict `==` returns false between Fluids.WATER and Fluids.FLOWING_WATER.
        // Compare by FluidType, which is shared between the two variants of the
        // same base fluid (e.g. NeoForgeMod.WATER_TYPE for both water variants).
        return a == b || a.getFluidType() == b.getFluidType();
    }

    /** 
     * Moves fluid between two generic ResourceHandlers.
     * @return The fluidstack that was moved, or null if no fluid was moved. 
     */
    //? if >=1.21.10 {
    @Nullable
    public static FluidStack move(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to) {
        return move(from, to, Integer.MAX_VALUE);
    }

    /**
     * Moves a maximum amount of fluid between two generic ResourceHandlers.
     * @param max The maximum amount of fluid to move.
     * @return The fluidstack that was moved, or null if no fluid was moved.
     */
    @Nullable
    public static FluidStack move(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to, int max) {
        if (from == null || to == null) {
            return null;
        }

        try (Transaction tx = Transaction.openRoot()) {
            // Check what fluid we have before moving, so we can return the correct stack
            FluidResource firstAvailable = FluidResource.EMPTY;
            for (int i = 0; i < from.size(); i++) {
                if (!from.getResource(i).isEmpty()) {
                    firstAvailable = from.getResource(i);
                    break;
                }
            }

            if (firstAvailable.isEmpty()) return null;

            int moved = ResourceHandlerUtil.move(from, to, r -> true, max, tx);
            if (moved > 0) {
                tx.commit();
                return firstAvailable.toStack(moved);
            }
        }
        return null;
    }
    //?} else {
    /*@Nullable
    public static FluidStack move(IFluidHandler from, IFluidHandler to) {
        return move(from, to, Integer.MAX_VALUE);
    }

    @Nullable
    public static FluidStack move(IFluidHandler from, IFluidHandler to, int max) {
        if (from == null || to == null) {
            return null;
        }
        FluidStack moved = FluidUtil.tryFluidTransfer(to, from, max, true);
        return moved.isEmpty() ? null : moved;
    }*/
    //?}

    /** @return A debug string representation of the given fluid stack, or "empty" if empty. */
    public static String getDebugString(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        return stack.getAmount() + " mB " + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
    }

    /**
     * Returns whether the given fluid is gaseous (lighter than air).
     * In NeoForge 1.21.11 this checks {@code FluidType.isLighterThanAir()},
     * which returns true when density ≤ 0 — equivalent to the old
     * {@code Fluid.isGaseous()} from Forge 1.12.2.
     */
    public static boolean isGaseous(FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getFluid().getFluidType().isLighterThanAir();
    }

    /** Overload for bare {@link Fluid} instances. */
    public static boolean isGaseous(Fluid fluid) {
        return fluid != null && fluid.getFluidType().isLighterThanAir();
    }

    //? if >=1.21.10 {
    public static boolean onTankActivated(Player player, BlockPos pos, InteractionHand hand,
        ResourceHandler<FluidResource> fluidHandler) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return false;
        Level world = player.level();
        if (world.isClientSide()) return true;

        boolean isCreative = player.getAbilities().instabuild;
        ItemStack singleCopy = held.copyWithCount(1);
        ResourceHandler<FluidResource> itemHandlerIn = singleCopy.getCapability(Capabilities.Fluid.ITEM, net.neoforged.neoforge.transfer.access.ItemAccess.forPlayerInteraction(player, hand));
        if (itemHandlerIn != null) {
            // First try draining tank -> item (filling bucket)
            try (Transaction tx = Transaction.openRoot()) {
                FluidResource tankFluid = fluidHandler.size() > 0 ? fluidHandler.getResource(0) : FluidResource.EMPTY;
                int moved = ResourceHandlerUtil.move(
                        fluidHandler, itemHandlerIn, r -> true, Integer.MAX_VALUE, tx
                );
                if (moved > 0) {
                    tx.commit();
                    if (!tankFluid.isEmpty()) SoundUtil.playBucketFill(world, pos, tankFluid.toStack(moved));
                    
                    if (isCreative) return true;
                    if (held.getCount() == 1) {
                        player.setItemInHand(hand, singleCopy);
                    } else {
                        held.shrink(1);
                        if (!singleCopy.isEmpty() && !player.getInventory().add(singleCopy)) {
                            player.drop(singleCopy, false);
                        }
                    }
                    player.containerMenu.broadcastChanges();
                    return true;
                }
            }

            // Next try item -> tank (emptying bucket)
            try (Transaction tx = Transaction.openRoot()) {
                FluidResource itemFluid = itemHandlerIn.size() > 0 ? itemHandlerIn.getResource(0) : FluidResource.EMPTY;
                int moved = ResourceHandlerUtil.move(
                        itemHandlerIn, fluidHandler, r -> true, Integer.MAX_VALUE, tx
                );
                if (moved > 0) {
                    tx.commit();
                    if (!itemFluid.isEmpty()) SoundUtil.playBucketEmpty(world, pos, itemFluid.toStack(moved));

                    if (isCreative) return true;
                    if (held.getCount() == 1) {
                        player.setItemInHand(hand, singleCopy);
                    } else {
                        held.shrink(1);
                        if (!singleCopy.isEmpty() && !player.getInventory().add(singleCopy)) {
                            player.drop(singleCopy, false);
                        }
                    }
                    player.containerMenu.broadcastChanges();
                    return true;
                }
            }
        }
        return false;
    }
    //?} else {
    /*public static boolean onTankActivated(Player player, BlockPos pos, InteractionHand hand,
        IFluidHandler fluidHandler) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return false;
        Level world = player.level();
        if (world.isClientSide()) return true;
        return FluidUtil.interactWithFluidHandler(player, hand, fluidHandler);
    }*/
    //?}

    // ---- Item fluid containers -------------------------------------------------------------------------------
    // NeoForge deprecated the classic net.neoforged.neoforge.fluids.FluidUtil (and IFluidHandler / FluidActionResult)
    // for removal at 1.21.9. These four helpers are the single seam every BuildCraft call site uses to read, fill or
    // empty a fluid container item, so the call sites stay directive-free: on 1.21.10+ they ride the Transfer API
    // (Capabilities.Fluid.ITEM through an ItemAccess); on 1.21.1 the classic helpers are still current and are used
    // as-is. FluidContainerHelperTest pins that both halves behave the same.

    /**
     * The fluid held by ONE item of the given stack (the stack's count is ignored), or {@link FluidStack#EMPTY} for
     * an empty container, a non-container or the empty stack. Never modifies {@code stack}.
     */
    public static FluidStack getFluidContained(ItemStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        //? if >=1.21.10 {
        // Reads through a one-by-one ItemAccess over the stack: getResource/getAmount only, nothing is extracted.
        return net.neoforged.neoforge.transfer.fluid.FluidUtil.getFirstStackContained(stack);
        //?} else {
        /*return FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);*/
        //?}
    }

    /** Whether the item exposes an item fluid handler at all — empty containers (e.g. an empty bucket) included. */
    public static boolean isFluidContainer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        //? if >=1.21.10 {
        return ItemAccess.forStack(stack).oneByOne().getCapability(Capabilities.Fluid.ITEM) != null;
        //?} else {
        /*return FluidUtil.getFluidHandler(stack.copyWithCount(1)).isPresent();*/
        //?}
    }

    /**
     * Empties the single fluid container sitting in {@code slots[slot]} into {@code tank}, swapping it in place for
     * whatever the container becomes (an empty bucket, or nothing for a consumable one). A stack of more than one
     * container is left alone — there is nowhere to put the emptied item, and swapping the whole stack for one
     * result would delete the rest.
     *
     * @return true if any fluid moved.
     */
    public static boolean drainContainerSlot(ItemHandlerSimple slots, int slot, BCFluidTank tank) {
        ItemStack stack = slots.getStackInSlot(slot);
        if (stack.getCount() != 1) {
            return false; // an empty slot, or a stack with nowhere to put the swapped container
        }
        //? if >=1.21.10 {
        ResourceHandler<FluidResource> container =
                ItemAccess.forHandlerIndexStrict(slots, slot).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        return container != null && moveAll(container, tank);
        //?} else {
        /*FluidActionResult result = FluidUtil.tryEmptyContainer(stack, tank, Integer.MAX_VALUE, null, true);
        if (result.isSuccess()) {
            slots.setStackInSlot(slot, result.getResult());
        }
        return result.isSuccess();*/
        //?}
    }

    /**
     * Fills the single fluid container sitting in {@code slots[slot]} from {@code tank}, swapping it in place for the
     * filled item. A stack of more than one container is left alone, as in {@link #drainContainerSlot}.
     *
     * @return true if any fluid moved.
     */
    public static boolean fillContainerSlot(ItemHandlerSimple slots, int slot, BCFluidTank tank) {
        ItemStack stack = slots.getStackInSlot(slot);
        if (stack.getCount() != 1) {
            return false; // an empty slot, or a stack with nowhere to put the swapped container
        }
        //? if >=1.21.10 {
        ResourceHandler<FluidResource> container =
                ItemAccess.forHandlerIndexStrict(slots, slot).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        return container != null && moveAll(tank, container);
        //?} else {
        /*FluidActionResult result = FluidUtil.tryFillContainer(stack, tank, Integer.MAX_VALUE, null, true);
        if (result.isSuccess()) {
            slots.setStackInSlot(slot, result.getResult());
        }
        return result.isSuccess();*/
        //?}
    }

    //? if >=1.21.10 {
    // The container handlers above come from a STRICT slot access: the filled/emptied item replaces the container in
    // that same slot, never overflowing into the tile's other slots. Everything moves in one root transaction.
    private static boolean moveAll(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to) {
        try (Transaction tx = Transaction.openRoot()) {
            if (ResourceHandlerUtil.move(from, to, r -> true, Integer.MAX_VALUE, tx) > 0) {
                tx.commit();
                return true;
            }
        }
        return false;
    }
    //?}

    public static ItemStack getFilledBucket(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (fluidStack.getComponents().isEmpty()) {
            if (fluidStack.is(Fluids.WATER)) {
                return new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
            } else if (fluidStack.is(Fluids.LAVA)) {
                return new ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET);
            }
        }
        return fluidStack.getFluidType().getBucket(fluidStack);
    }
}
