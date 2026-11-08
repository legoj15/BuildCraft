/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.MapMaker;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
//? if >=26.2 {
import net.minecraft.world.entity.EntityTypes;
//?}
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
//? if >=1.21.11 {
import net.minecraft.world.entity.animal.equine.AbstractHorse;
//?} else {
/^import net.minecraft.world.entity.animal.horse.AbstractHorse;
^///?}
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
//? if >=26.1 {
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
//?} else {
/^import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
^///?}
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/^*
 * An entity's whole inventory, the Fabric twin of NeoForge's {@code Capabilities.Item.ENTITY}: Fabric API has no
 * standard entity item lookup, so BuildCraft keeps its own ({@code buildcraftunofficial:item_storage}, no context),
 * which {@code TransferAccess.findItem(Entity)} reads ({@link FabricTransfer#findItem}). Another mod (or a test) offers
 * its entity's items by registering a provider on {@link #LOOKUP}.
 *
 * <p>Registered here, as NeoForge registers them: the vanilla container entities (the chest boats and raft, the chest
 * and hopper minecarts) as their {@code Container}, and the player as its inventory. Then NeoForge's fallback
 * ({@code CapabilityHooks.registerFallbackVanillaProviders}), asked only when no provider for the type answers: a
 * horse, donkey, mule or llama as its inventory, and any other living entity as its two hands then its four armour
 * slots ({@link #equipment}).
 *^/
public final class FabricEntityItems {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final EntityApiLookup<Storage<ItemVariant>, Void> LOOKUP = EntityApiLookup.get(
            Identifier.fromNamespaceAndPath("buildcraftunofficial", "item_storage"), (Class) Storage.class, Void.class);

    static {
        LOOKUP.registerForTypes((entity, context) -> containerStorage((Container) entity),
                containerEntities());
        //? if >=26.2 {
        LOOKUP.registerForType((player, context) -> PlayerInventoryStorage.of(player), EntityTypes.PLAYER);
        //?} else {
        /^LOOKUP.registerForType((player, context) -> PlayerInventoryStorage.of(player), EntityType.PLAYER);
        ^///?}
        LOOKUP.registerFallback((entity, context) -> {
            if (entity instanceof AbstractHorse horse) {
                return containerStorage(horse.inventory);
            } else if (entity instanceof LivingEntity living) {
                return equipment(living);
            }
            return null;
        });
    }

    /^* Each living entity's equipment storage, for as long as anything holds it (NeoForge: a weak map of wrappers). ^/
    private static final Map<LivingEntity, Storage<ItemVariant>> EQUIPMENT = new MapMaker().weakKeys().weakValues().makeMap();

    private FabricEntityItems() {}

    /^* A container's whole storage, no side (Fabric API's {@code ContainerStorage}, {@code InventoryStorage} before
     *  26.1). *^/
    private static Storage<ItemVariant> containerStorage(Container container) {
        //? if >=26.1 {
        return ContainerStorage.of(container, null);
        //?} else {
        /^return InventoryStorage.of(container, null);
        ^///?}
    }

    private static EntityType<?>[] containerEntities() {
        return new EntityType<?>[] {
            //? if >=26.2 {
            EntityTypes.ACACIA_CHEST_BOAT,
            EntityTypes.BIRCH_CHEST_BOAT,
            EntityTypes.CHERRY_CHEST_BOAT,
            EntityTypes.DARK_OAK_CHEST_BOAT,
            EntityTypes.JUNGLE_CHEST_BOAT,
            EntityTypes.MANGROVE_CHEST_BOAT,
            EntityTypes.OAK_CHEST_BOAT,
            EntityTypes.SPRUCE_CHEST_BOAT,
            EntityTypes.BAMBOO_CHEST_RAFT,
            EntityTypes.PALE_OAK_CHEST_BOAT,
            //? if >=26.3 {
            EntityTypes.POPLAR_CHEST_BOAT,
            //?}
            EntityTypes.CHEST_MINECART,
            EntityTypes.HOPPER_MINECART,
            //?} elif >=1.21.10 {
            /^EntityType.ACACIA_CHEST_BOAT,
            EntityType.BIRCH_CHEST_BOAT,
            EntityType.CHERRY_CHEST_BOAT,
            EntityType.DARK_OAK_CHEST_BOAT,
            EntityType.JUNGLE_CHEST_BOAT,
            EntityType.MANGROVE_CHEST_BOAT,
            EntityType.OAK_CHEST_BOAT,
            EntityType.SPRUCE_CHEST_BOAT,
            EntityType.BAMBOO_CHEST_RAFT,
            EntityType.PALE_OAK_CHEST_BOAT,
            EntityType.CHEST_MINECART,
            EntityType.HOPPER_MINECART,
            ^///?} else {
            /^EntityType.CHEST_BOAT,
            EntityType.CHEST_MINECART,
            EntityType.HOPPER_MINECART,
            ^///?}
        };
    }

    /^*
     * NeoForge's {@code LivingEntityEquipmentWrapper} hands and humanoid-armour handlers, combined: one slot per
     * {@link EquipmentSlot} of those types in declaration order. A slot takes what the entity can
     * equip there ({@code isEquippableInSlot}, NeoForge's default {@code canEquip}; on 1.21.1 any item, as NeoForge's
     * wrapper there), up to the slot's count limit (one
     * in an armour slot); the stack is set without side
     * effects while the transaction is open and {@code onEquipItem} runs once the outer transaction commits.
     *
     * <p>Slotted on purpose: a plain {@code CombinedStorage} is no {@code SlottedStorage}, so the adapter
     * ({@code FabricItemStorages.fromFabric}) would fall back to its one-virtual-slot view and lose the slot
     * indices NeoForge's capability keeps.
     *
     * <p>One storage per entity, as NeoForge's wrapper is ({@link #EQUIPMENT}, weakly held): Fabric's entity lookup
     * caches nothing, and two storages over the same slot would each snapshot it and each report the equip change when
     * one outer transaction touched both.
     ^/
    private static Storage<ItemVariant> equipment(LivingEntity entity) {
        return EQUIPMENT.computeIfAbsent(entity, FabricEntityItems::newEquipment);
    }

    private static Storage<ItemVariant> newEquipment(LivingEntity entity) {
        List<SlotStorage> slots = new ArrayList<>();
        for (EquipmentSlot.Type type : List.of(EquipmentSlot.Type.HAND, EquipmentSlot.Type.HUMANOID_ARMOR)) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() == type) {
                    slots.add(new SlotStorage(entity, slot));
                }
            }
        }
        return new CombinedSlottedStorage<>(slots);
    }

    private static final class SlotStorage extends SingleStackStorage {
        private final LivingEntity entity;
        private final EquipmentSlot slot;
        /^*
         * How many the slot holds at most, whatever the item: one for a humanoid-armour slot, no limit (the absolute
         * stack maximum) for a hand. {@code EquipmentSlot.limit} splits the limit off the stack it is given, which is
         * the one way to read it (the field is private).
         ^/
        private final int countLimit;
        /^* The stack before the open outer transaction first touched this slot, for {@code onEquipItem}. *^/
        private @Nullable ItemStack original;

        SlotStorage(LivingEntity entity, EquipmentSlot slot) {
            this.entity = entity;
            this.slot = slot;
            this.countLimit = slot.limit(new ItemStack(Items.STONE, Item.ABSOLUTE_MAX_STACK_SIZE)).getCount();
        }

        @Override
        protected ItemStack getStack() {
            return entity.getItemBySlot(slot);
        }

        @Override
        protected void setStack(ItemStack stack) {
            //? if >=1.21.10 {
            // Vanilla's setItemSlot is this plus onEquipItem, which waits for the commit (NeoForge's
            // setItemSlot(slot, stack, insideTransaction = true)).
            entity.equipment.set(slot, stack);
            //?} else {
            /^// 1.21.1 keeps equipment per entity class, with no setter that skips onEquipItem: setItemSlot, as NeoForge's
            // 1.21.1 wrapper sets it (each set, a rollback's too, runs onEquipItem; onFinalCommit adds none).
            entity.setItemSlot(slot, stack);
            ^///?}
        }

        @Override
        protected boolean canInsert(ItemVariant variant) {
            //? if >=1.21.10 {
            return entity.isEquippableInSlot(variant.toStack(), slot);
            //?} else {
            /^return true; // NeoForge's 1.21.1 wrapper takes any item in any slot
            ^///?}
        }

        @Override
        protected int getCapacity(ItemVariant variant) {
            // The slot's own count limit, then the item's stack size, as NeoForge's wrapper caps it.
            return Math.min(countLimit, variant.toStack().getMaxStackSize());
        }

        @Override
        public void updateSnapshots(TransactionContext transaction) {
            if (original == null) {
                original = getStack().copy();
            }
            super.updateSnapshots(transaction);
        }

        @Override
        protected void onFinalCommit() {
            //? if >=1.21.10 {
            if (original != null) {
                entity.onEquipItem(slot, original, getStack());
            }
            //?}
        }

        @Override
        public void afterOuterClose(TransactionContext.Result result) {
            super.afterOuterClose(result);
            original = null;
        }

        @Override
        public String toString() {
            return "entity equipment storage[entity=" + entity + ",slot=" + slot + "]";
        }
    }
}
*///?}
