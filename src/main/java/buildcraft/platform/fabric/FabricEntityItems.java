/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

/^*
 * An entity's whole inventory, the Fabric twin of NeoForge's {@code Capabilities.Item.ENTITY}: Fabric API has no
 * standard entity item lookup, so BuildCraft keeps its own ({@code buildcraftunofficial:item_storage}, no context),
 * which {@code TransferAccess.findItem(Entity)} reads ({@link FabricTransfer#findItem}). Another mod (or a test) offers
 * its entity's items by registering a provider on {@link #LOOKUP}.
 *
 * <p>Registered here, as NeoForge registers them: the vanilla container entities (the chest boats and raft, the chest
 * and hopper minecarts) as their {@code Container}, and the player as its inventory. Gap: NeoForge's fallback for
 * every other living entity (a horse's inventory, or the hands and armour) is not mirrored.
 *^/
public final class FabricEntityItems {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final EntityApiLookup<Storage<ItemVariant>, Void> LOOKUP = EntityApiLookup.get(
            Identifier.fromNamespaceAndPath("buildcraftunofficial", "item_storage"), (Class) Storage.class, Void.class);

    static {
        LOOKUP.registerForTypes((entity, context) -> ContainerStorage.of((Container) entity, null),
                containerEntities());
        LOOKUP.registerForType((player, context) -> PlayerInventoryStorage.of(player), EntityTypes.PLAYER);
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
}
*///?}
