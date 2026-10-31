/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.stripes;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
//? if >=1.21.11 {
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
//?} else {
/*import net.minecraft.world.entity.vehicle.MinecartChest;*/
//?}
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.transport.IStripesActivator;

import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.test.CapabilityOnlyCart;
import buildcraft.lib.test.EntityArenaUtil;

/**
 * The stripes minecart handler never makes anything it takes apart disappear: what a cart carries and the cart itself
 * go down the pipe, or stay where they were. The handler is run directly (not through a pipe's tick) with an activator
 * that records what it was sent, against chest carts that misbehave the way a modded cart can.
 */
public class StripesHandlerMinecartDestroyTester {

    private static final int POLL_TICKS = 60;

    /** Records what the handler hands the pipe. */
    private static final class Recorder implements IStripesActivator {
        final List<ItemStack> sent = new ArrayList<>();

        @Override
        public boolean sendItem(@Nonnull ItemStack itemStack, Direction from) {
            sent.add(itemStack);
            return true;
        }

        @Override
        public void dropItem(@Nonnull ItemStack itemStack, Direction from) {
            throw new IllegalStateException("the handler never drops by itself, it sends or leaves alone");
        }

        int count(Item item) {
            int found = 0;
            for (ItemStack stack : sent) {
                if (stack.is(item)) {
                    found += stack.getCount();
                }
            }
            return found;
        }
    }

    /**
     * A chest cart as a mod can make one: one slot that ignores being emptied (a container whose
     * {@code setItem(EMPTY)} and {@code removeItem} do not take, and {@code getItem} hands out copies), and a cart that
     * names no item to hand back. Nothing
     * vanilla does either. It is still a chest cart to NeoForge, so the handler reaches it through the entity item
     * capability that NeoForge registers for chest carts, over this very container.
     */
    private static final class OddChestCart extends MinecartChest {
        static final int STUBBORN_SLOT = 1;
        boolean stubborn;
        boolean namesNoItem;

        OddChestCart(Level level) {
            //? if >=26.2 {
            super(net.minecraft.world.entity.EntityTypes.CHEST_MINECART, level);
            //?} else {
            /*super(net.minecraft.world.entity.EntityType.CHEST_MINECART, level);*/
            //?}
        }

        @Override
        public void setItem(int slot, @Nonnull ItemStack stack) {
            if (stubborn && slot == STUBBORN_SLOT && stack.isEmpty()) {
                return;
            }
            super.setItem(slot, stack);
        }

        /** The Transfer API (1.21.10+) shrinks the stack {@code getItem} returns in place, then calls {@code setItem},
         *  so a slot only ignores being emptied if it hands out a copy. */
        @Override
        public ItemStack getItem(int slot) {
            ItemStack held = super.getItem(slot);
            return stubborn && slot == STUBBORN_SLOT ? held.copy() : held;
        }

        /** The classic item handler (1.21.1) empties a slot through this, not through {@code setItem}. */
        @Override
        public ItemStack removeItem(int slot, int count) {
            return stubborn && slot == STUBBORN_SLOT ? ItemStack.EMPTY : super.removeItem(slot, count);
        }

        @Override
        public ItemStack getPickResult() {
            return namesNoItem ? ItemStack.EMPTY : super.getPickResult();
        }
    }

    private static OddChestCart spawnCart(GameTestHelper helper, BlockPos relPos) {
        ServerLevel level = helper.getLevel();
        OddChestCart cart = new OddChestCart(level);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(relPos));
        cart.setPos(at.x, at.y, at.z);
        cart.setNoGravity(true);
        cart.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        cart.setItem(OddChestCart.STUBBORN_SLOT, new ItemStack(Items.DIAMOND, 2));
        level.addFreshEntity(cart);
        return cart;
    }

    private static void discardItems(GameTestHelper helper, BlockPos relPos) {
        for (ItemEntity item : EntityArenaUtil.droppedItems(helper, relPos, 2.0)) {
            item.discard();
        }
    }

    /**
     * A cart whose slot will not empty is left alone: what could be taken (the cobblestone) goes down the pipe, and
     * the cart stays with the item that would not come out, instead of being discarded around it. Sending that item
     * as well would duplicate it (it is still in the cart), and discarding the cart loses it or, for a chest cart,
     * spills it on the ground. The control is the same cart with an ordinary slot: all of it goes down the pipe, the
     * cart included, and the cart is gone.
     */
    public static void testSlotThatWillNotClearKeepsTheCartAndItsItem(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos frontRel = pipeRel.east();
        EntityArenaUtil.forceLoadEntityArena(helper, frontRel);
        OddChestCart[] carts = new OddChestCart[2];
        EntityArenaUtil.tickUntil(helper, POLL_TICKS, () -> EntityArenaUtil.isEntityTicking(helper, frontRel), () -> {
            try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(helper.getLevel())) {
                carts[0] = spawnCart(helper, frontRel);
                carts[0].stubborn = true;
                Recorder stuck = new Recorder();
                boolean acted = StripesHandlerMinecartDestroy.INSTANCE.handle(
                        helper.getLevel(), helper.absolutePos(pipeRel), Direction.EAST, lease.player(), stuck);

                helper.assertTrue(acted, "taking the cobblestone out of the cart is the handler acting");
                helper.assertTrue(stuck.count(Items.COBBLESTONE) == 4,
                        "the slot that emptied goes down the pipe, found " + stuck.count(Items.COBBLESTONE));
                helper.assertTrue(stuck.count(Items.DIAMOND) == 0,
                        "...but the item still in the cart is not sent as well, found " + stuck.count(Items.DIAMOND));
                helper.assertTrue(stuck.count(Items.CHEST_MINECART) == 0, "...nor is the cart's own item");
                helper.assertTrue(!carts[0].isRemoved(), "a cart with an item that would not come out is left alone");
                helper.assertTrue(carts[0].getItem(OddChestCart.STUBBORN_SLOT).is(Items.DIAMOND)
                        && carts[0].getItem(OddChestCart.STUBBORN_SLOT).getCount() == 2,
                        "...with that item still in it, found " + carts[0].getItem(OddChestCart.STUBBORN_SLOT));
                helper.assertTrue(EntityArenaUtil.droppedItems(helper, frontRel, 2.0).isEmpty(),
                        "...and nothing spilled on the ground");

                // Control: the same cart, its slot an ordinary one. Everything goes down the pipe and the cart is gone.
                EntityArenaUtil.discard(carts[0]);
                carts[1] = spawnCart(helper, frontRel);
                Recorder whole = new Recorder();
                boolean wholeActed = StripesHandlerMinecartDestroy.INSTANCE.handle(
                        helper.getLevel(), helper.absolutePos(pipeRel), Direction.EAST, lease.player(), whole);
                helper.assertTrue(wholeActed, "control: an ordinary chest cart is broken");
                helper.assertTrue(whole.count(Items.COBBLESTONE) == 4 && whole.count(Items.DIAMOND) == 2
                        && whole.count(Items.CHEST_MINECART) == 1,
                        "control: the contents and the cart go down the pipe, found " + whole.sent);
                helper.assertTrue(carts[1].isRemoved(), "control: ...and the cart is gone");
            }
            helper.succeed();
        }, "the cart's chunk never became entity-ticking", () -> {
            EntityArenaUtil.discard(carts);
            discardItems(helper, frontRel);
        });
    }

    /**
     * A cart that names no item to hand back is not simply deleted: the handler lets it be destroyed its own way, the
     * way an attack would (its drop lands in the world), after the contents have gone down the pipe. It used to
     * discard the cart whatever it had to return, which for a cart with no pick result lost the cart.
     */
    public static void testCartThatNamesNoItemIsDestroyedItsOwnWay(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos frontRel = pipeRel.east();
        EntityArenaUtil.forceLoadEntityArena(helper, frontRel);
        OddChestCart[] cart = new OddChestCart[1];
        EntityArenaUtil.tickUntil(helper, POLL_TICKS, () -> EntityArenaUtil.isEntityTicking(helper, frontRel), () -> {
            try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(helper.getLevel())) {
                cart[0] = spawnCart(helper, frontRel);
                cart[0].namesNoItem = true;
                Recorder recorder = new Recorder();
                boolean acted = StripesHandlerMinecartDestroy.INSTANCE.handle(
                        helper.getLevel(), helper.absolutePos(pipeRel), Direction.EAST, lease.player(), recorder);

                helper.assertTrue(acted, "the cart is broken");
                helper.assertTrue(recorder.count(Items.COBBLESTONE) == 4 && recorder.count(Items.DIAMOND) == 2,
                        "the contents go down the pipe, found " + recorder.sent);
                helper.assertTrue(cart[0].isRemoved(), "the cart is gone");
                boolean dropped = false;
                for (ItemEntity item : EntityArenaUtil.droppedItems(helper, frontRel, 2.0)) {
                    dropped |= item.getItem().is(Items.CHEST_MINECART);
                }
                helper.assertTrue(dropped, "...and it left its own drop in the world instead of vanishing");
            }
            helper.succeed();
        }, "the cart's chunk never became entity-ticking", () -> {
            EntityArenaUtil.discard(cart);
            discardItems(helper, frontRel);
        });
    }

    /**
     * A cart that offers its items only through the entity item capability (it is not a {@code Container}) is emptied
     * through it before it is broken: what it carries goes down the pipe along with the cart, and nothing is left in
     * it. The handler used to read only a vanilla {@code Container}, so such a cart was discarded with its contents
     * unsent and they were lost.
     */
    public static void testCapabilityOnlyCartIsEmptiedBeforeItIsBroken(GameTestHelper helper) {
        BlockPos pipeRel = new BlockPos(2, 2, 2);
        BlockPos frontRel = pipeRel.east();
        EntityArenaUtil.forceLoadEntityArena(helper, frontRel);
        CapabilityOnlyCart[] cart = new CapabilityOnlyCart[1];
        EntityArenaUtil.tickUntil(helper, POLL_TICKS, () -> EntityArenaUtil.isEntityTicking(helper, frontRel), () -> {
            try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(helper.getLevel())) {
                cart[0] = new CapabilityOnlyCart(helper.getLevel());
                Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(frontRel));
                cart[0].setPos(at.x, at.y, at.z);
                cart[0].setNoGravity(true);
                cart[0].store.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 4));
                cart[0].store.setStackInSlot(2, new ItemStack(Items.DIAMOND, 2));
                helper.getLevel().addFreshEntity(cart[0]);

                Recorder recorder = new Recorder();
                boolean acted = StripesHandlerMinecartDestroy.INSTANCE.handle(
                        helper.getLevel(), helper.absolutePos(pipeRel), Direction.EAST, lease.player(), recorder);

                helper.assertTrue(acted, "the cart is broken");
                helper.assertTrue(recorder.count(Items.COBBLESTONE) == 4 && recorder.count(Items.DIAMOND) == 2,
                        "what the cart carried goes down the pipe, found " + recorder.sent);
                helper.assertTrue(recorder.count(Items.MINECART) == 1, "...with the cart's own item");
                helper.assertTrue(cart[0].store.getStackInSlot(0).isEmpty() && cart[0].store.getStackInSlot(2).isEmpty(),
                        "...and none of it is left in the cart, found " + cart[0].store.stacks);
                helper.assertTrue(cart[0].isRemoved(), "the cart is gone");
            }
            helper.succeed();
        }, "the cart's chunk never became entity-ticking", () -> {
            EntityArenaUtil.discard(cart);
            discardItems(helper, frontRel);
        });
    }
}
