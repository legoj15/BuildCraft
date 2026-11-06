/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
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

    /** Every living entity's storage index to the equipment slot it reaches. */
    private static final EquipmentSlot[] SLOT_ORDER = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
        EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD,
    };
    /**
     * An item each {@link #SLOT_ORDER} slot can equip: vanilla's {@code isEquippableInSlot} lets an item with no
     * {@code equippable} component into the main hand only, so the off hand gets a shield.
     */
    private static final Item[] FITS = {
        Items.STICK, Items.SHIELD,
        Items.IRON_BOOTS, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE, Items.IRON_HELMET,
    };
    /** The head slot's index: last in {@link #SLOT_ORDER}. */
    private static final int HEAD = SLOT_ORDER.length - 1;

    /**
     * Offers a fresh copy of {@code template}, so no call can see what an earlier one did to its stack: the storage
     * may mutate what it is handed (NeoForge's raw {@code InvWrapper.insertItem} does), and the stack is both the
     * filter and the payload.
     */
    private static int insert(BCItemStorage storage, int slot, ItemStack template) {
        ItemStack offer = template.copy();
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int moved = storage.insert(slot, offer, offer.getCount(), tx);
            tx.commit();
            return moved;
        }
    }

    /** Asks for a fresh copy of {@code template} back, for the same reason as {@link #insert}. */
    private static int extract(BCItemStorage storage, int slot, ItemStack template) {
        ItemStack request = template.copy();
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int moved = storage.extract(slot, request, request.getCount(), tx);
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
     * equip there (the main hand anything not equippable elsewhere, the off hand and armour only their own
     * equippables). The equippability filter is NeoForge 21.10+ and Fabric: 1.21.1's wrappers insert into any
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
        helper.assertTrue(storage.size() == SLOT_ORDER.length,
                "two hands then four armour slots (size " + storage.size() + ")");

        // The order is NeoForge's (hands, then armour in EquipmentSlot order) on every line, and the Fabric twin's:
        // each index round-trips an item that fits there and must land in exactly that equipment slot.
        for (int i = 0; i < SLOT_ORDER.length; i++) {
            EquipmentSlot expected = SLOT_ORDER[i];
            ItemStack fits = new ItemStack(FITS[i]);
            helper.assertTrue(insert(storage, i, fits) == 1, "slot " + i + " takes " + fits.getItem());
            helper.assertTrue(zombie.getItemBySlot(expected).is(FITS[i]), "slot " + i + " is " + expected);
            for (EquipmentSlot other : SLOT_ORDER) {
                helper.assertTrue(other == expected || zombie.getItemBySlot(other).isEmpty(),
                        "slot " + i + " equipped nothing in " + other);
            }
            helper.assertTrue(storage.getStack(i).is(FITS[i]), "the storage reads slot " + i + " back");
            helper.assertTrue(extract(storage, i, fits) == 1, "slot " + i + " gives its item back");
            helper.assertTrue(zombie.getItemBySlot(expected).isEmpty(), expected + " is empty again");
        }

        //? if >=1.21.10 {
        ItemStack stick = new ItemStack(Items.STICK);
        for (int i = 1; i < SLOT_ORDER.length; i++) {
            helper.assertTrue(insert(storage, i, stick) == 0, "slot " + i + " (" + SLOT_ORDER[i] + ") refuses a stick");
            helper.assertTrue(zombie.getItemBySlot(SLOT_ORDER[i]).isEmpty(), "no stick was equipped in slot " + i);
        }
        // An armour item fits only its own slot, not even the main hand.
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        for (int i = 0; i < HEAD; i++) {
            helper.assertTrue(insert(storage, i, helmet) == 0,
                    "slot " + i + " (" + SLOT_ORDER[i] + ") refuses a helmet");
            helper.assertTrue(zombie.getItemBySlot(SLOT_ORDER[i]).isEmpty(), "no helmet was equipped in slot " + i);
        }

        // An armour slot holds one item however many fit its stack (NeoForge's wrapper caps it at the slot's count
        // limit; a hand has none): eight carved pumpkins offered to the head slot, one goes on.
        ItemStack pumpkins = new ItemStack(Items.CARVED_PUMPKIN, 8);
        helper.assertTrue(insert(storage, HEAD, pumpkins) == 1, "the head slot takes one of eight pumpkins");
        helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.HEAD).getCount() == 1, "and holds one");
        helper.assertTrue(extract(storage, HEAD, pumpkins) == 1, "the pumpkin comes back out");
        ItemStack sticks = new ItemStack(Items.STICK, 20);
        helper.assertTrue(insert(storage, 0, sticks) == 20, "a hand has no count limit, only the stack's");
        helper.assertTrue(extract(storage, 0, sticks) == 20, "the sticks come back out of the hand");
        //?}
    }
}
