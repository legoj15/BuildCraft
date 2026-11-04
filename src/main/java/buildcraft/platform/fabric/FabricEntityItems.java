/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
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
        LOOKUP.registerForTypes((entity, context) -> ContainerStorage.of((Container) entity, null),
                containerEntities());
        LOOKUP.registerForType((player, context) -> PlayerInventoryStorage.of(player), EntityTypes.PLAYER);
        LOOKUP.registerFallback((entity, context) -> {
            if (entity instanceof AbstractHorse horse) {
                return ContainerStorage.of(horse.inventory, null);
            } else if (entity instanceof LivingEntity living) {
                return equipment(living);
            }
            return null;
        });
    }

    private FabricEntityItems() {}

    private static EntityType<?>[] containerEntities() {
        return new EntityType<?>[] {
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
            EntityTypes.POPLAR_CHEST_BOAT,
            EntityTypes.CHEST_MINECART,
            EntityTypes.HOPPER_MINECART,
        };
    }

    /^*
     * NeoForge's {@code LivingEntityEquipmentWrapper} hands and humanoid-armour handlers, combined: one slot per
     * {@link EquipmentSlot} of those types in {@link EquipmentSlot#VALUES} order. A slot takes what the entity can
     * equip there ({@code isEquippableInSlot}, NeoForge's default {@code canEquip}); the stack is set without side
     * effects while the transaction is open and {@code onEquipItem} runs once the outer transaction commits.
     *^/
    private static Storage<ItemVariant> equipment(LivingEntity entity) {
        List<SlotStorage> slots = new ArrayList<>();
        for (EquipmentSlot.Type type : List.of(EquipmentSlot.Type.HAND, EquipmentSlot.Type.HUMANOID_ARMOR)) {
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                if (slot.getType() == type) {
                    slots.add(new SlotStorage(entity, slot));
                }
            }
        }
        return new CombinedStorage<>(slots);
    }

    private static final class SlotStorage extends SingleStackStorage {
        private final LivingEntity entity;
        private final EquipmentSlot slot;
        /^* The stack before the open outer transaction first touched this slot, for {@code onEquipItem}. *^/
        private @Nullable ItemStack original;

        SlotStorage(LivingEntity entity, EquipmentSlot slot) {
            this.entity = entity;
            this.slot = slot;
        }

        @Override
        protected ItemStack getStack() {
            return entity.getItemBySlot(slot);
        }

        @Override
        protected void setStack(ItemStack stack) {
            // Vanilla's setItemSlot is this plus onEquipItem, which waits for the commit (NeoForge's
            // setItemSlot(slot, stack, insideTransaction = true)).
            entity.equipment.set(slot, stack);
        }

        @Override
        protected boolean canInsert(ItemVariant variant) {
            return entity.isEquippableInSlot(variant.toStack(), slot);
        }

        @Override
        protected int getCapacity(ItemVariant variant) {
            // Hand and humanoid-armour slots have no count limit, so the cap is the item's own stack size.
            return variant.toStack().getMaxStackSize();
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
            if (original != null) {
                entity.onEquipItem(slot, original, getStack());
            }
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
