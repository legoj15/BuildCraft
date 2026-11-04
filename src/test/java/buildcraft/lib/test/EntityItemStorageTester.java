/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import buildcraft.platform.transfer.BCItemStorage;
import buildcraft.platform.transfer.BCTransaction;
import buildcraft.platform.transfer.TransferAccess;

/**
 * The living-entity item-storage fallback, read the way BuildCraft's entity item lookups read it
 * ({@link TransferAccess#findItem(net.minecraft.world.entity.Entity)}): an equine is its chest inventory, any other
 * living entity is its two hands then its four armour slots, each taking only what the entity can equip there.
 * NeoForge's own fallback capability ({@code CapabilityHooks.registerFallbackVanillaProviders}) and the Fabric twin
 * ({@code FabricEntityItems}) must answer the same through BuildCraft's storage view.
 */
public final class EntityItemStorageTester {
    private EntityItemStorageTester() {}

    private static int insert(BCItemStorage storage, int slot, ItemStack stack) {
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int moved = storage.insert(slot, stack, stack.getCount(), tx);
            tx.commit();
            return moved;
        }
    }

    private static int extract(BCItemStorage storage, int slot, ItemStack stack) {
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int moved = storage.extract(slot, stack, stack.getCount(), tx);
            tx.commit();
            return moved;
        }
    }

    public static void livingEntityItemStorageFallback(GameTestHelper helper) {
        //? if >=26.2 {
        Mob donkey = helper.spawn(net.minecraft.world.entity.EntityTypes.DONKEY, new net.minecraft.core.BlockPos(2, 2, 2));
        Mob zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new net.minecraft.core.BlockPos(4, 2, 2));
        //?} else {
        /*Mob donkey = helper.spawn(net.minecraft.world.entity.EntityType.DONKEY, new net.minecraft.core.BlockPos(2, 2, 2));
        Mob zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new net.minecraft.core.BlockPos(4, 2, 2));*/
        //?}
        try {
            for (Mob mob : new Mob[] { donkey, zombie }) {
                // Inert: nothing here may tick, and a wandering mob could leave its own arena cell.
                mob.setNoAi(true);
                mob.setNoGravity(true);
            }
            equineChestInventory(helper, donkey);
            equipmentSlots(helper, zombie);
        } finally {
            EntityArenaUtil.discard(donkey, zombie);
        }
        helper.succeed();
    }

    /**
     * An equine is its chest inventory — the container a player's chest creates, NOT the six equipment slots every
     * other living entity answers with. The chest is put on the vanilla way ({@code getSlot(499)}, what
     * {@code equipChest} goes through), because the inventory is only (re)created there: a plain equine carries a
     * zero-column chest grid. A chested donkey has five columns of three rows (plus the saddle slot on 1.21.1, whose
     * container predates equipment slots).
     */
    private static void equineChestInventory(GameTestHelper helper, Mob donkey) {
        helper.assertTrue(donkey.getSlot(499).set(new ItemStack(Items.CHEST)),
                "the chest goes on the donkey");

        BCItemStorage storage = TransferAccess.findItem(donkey);
        helper.assertTrue(storage != null, "a donkey has an item storage");
        helper.assertTrue(storage.size() >= 15, "a chested donkey is its 15-slot chest inventory, not the six"
                + " equipment slots (size " + storage.size() + ")");

        // A stick fits no equipment slot an equine can use, so a full round trip through slot 0 proves this view
        // reaches the container, not the equipment.
        ItemStack stick = new ItemStack(Items.STICK);
        helper.assertTrue(insert(storage, 0, stick) == 1, "the chest inventory's first slot takes a stick");
        helper.assertTrue(storage.getStack(0).is(Items.STICK), "the stick is kept in the chest inventory");
        helper.assertTrue(extract(storage, 0, stick) == 1, "the stick comes back out");
        helper.assertTrue(storage.getStack(0).isEmpty(), "the chest inventory's slot is empty after extraction");
    }

    /**
     * Every other living entity is its two hands then its four armour slots, each taking only what the entity can
     * equip there. The equippability filter is NeoForge 21.10+ and Fabric: 1.21.1's wrappers insert into any
     * equipment slot without one.
     */
    private static void equipmentSlots(GameTestHelper helper, Mob zombie) {
        // A spawn that is not finalised carries no random gear; clear the exposed slots to make sure.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HAND || slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                zombie.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        BCItemStorage storage = TransferAccess.findItem(zombie);
        helper.assertTrue(storage != null, "a zombie has an item storage");
        helper.assertTrue(storage.size() == 6, "two hands then four armour slots (size " + storage.size() + ")");

        ItemStack stick = new ItemStack(Items.STICK);
        helper.assertTrue(insert(storage, 0, stick) == 1, "the first hand slot takes a stick");
        helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.STICK),
                "the first slot is the main hand");
        helper.assertTrue(extract(storage, 0, stick) == 1, "extraction from the hand returns its stack");
        helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "the hand is empty again");

        //? if >=1.21.10 {
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        for (int i = 2; i < 6; i++) {
            helper.assertTrue(insert(storage, i, stick) == 0, "armour slot " + i + " refuses a stick");
        }
        helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "no stick was equipped");

        int accepting = -1;
        for (int i = 0; i < 6; i++) {
            if (insert(storage, i, helmet) == 1) {
                helper.assertTrue(accepting == -1, "only one slot takes the helmet (also slot " + i + ")");
                accepting = i;
                // Take it straight out again so the next slot is tried on an empty entity.
                helper.assertTrue(extract(storage, i, helmet) == 1, "the helmet comes back out of slot " + i);
            }
        }
        helper.assertTrue(accepting >= 2, "the helmet goes to an armour slot (slot " + accepting + ")");
        helper.assertTrue(insert(storage, accepting, helmet) == 1, "the helmet goes in again");
        helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                "the helmet is in the head slot");
        helper.assertTrue(storage.getStack(accepting).is(Items.IRON_HELMET), "the storage reads it back");
        helper.assertTrue(extract(storage, accepting, helmet) == 1, "the helmet comes back out of the head slot");
        helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "the head slot is empty again");
        //?}
    }
}
