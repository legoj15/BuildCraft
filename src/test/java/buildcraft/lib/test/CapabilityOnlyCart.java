/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
//? if >=1.21.11 {
import net.minecraft.world.entity.vehicle.minecart.Minecart;
//?} else {
/*import net.minecraft.world.entity.vehicle.Minecart;*/
//?}

//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
//?}

import buildcraft.lib.tile.item.ItemHandlerSimple;
//? if neoforge {
import buildcraft.platform.neoforge.NeoForgeItemStorages;
//?}


/**
 * A minecart as a mod can make one: not a vanilla {@code Container}, its items reachable only through the entity item
 * capability. The capability is registered on the plain minecart type (which has none of its own), answering only for
 * instances of this class, so every other minecart in a test world is untouched. Lives in the test source set only.
 * On Fabric the capability is BuildCraft's own entity item lookup (FabricEntityItems), registered by
 * {@code registerLookup} there.
 */
//? if neoforge {
@EventBusSubscriber(modid = "buildcraftunofficial")
//?}
public final class CapabilityOnlyCart extends Minecart {

    /** What the cart carries, and what its capability exposes. */
    public final ItemHandlerSimple store = new ItemHandlerSimple(3);

    public CapabilityOnlyCart(Level level) {
        super(minecartType(), level);
    }

    private static EntityType<Minecart> minecartType() {
        //? if >=26.2 {
        return net.minecraft.world.entity.EntityTypes.MINECART;
        //?} else {
        /*return EntityType.MINECART;*/
        //?}
    }

    //? if neoforge && >=1.21.10 {
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.Item.ENTITY, minecartType(),
                (entity, ctx) -> entity instanceof CapabilityOnlyCart cart ? NeoForgeItemStorages.toNeo(cart.store) : null);
    }
    //?} elif neoforge {
    /*@SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.ItemHandler.ENTITY, minecartType(),
                (entity, ctx) -> entity instanceof CapabilityOnlyCart cart ? NeoForgeItemStorages.toNeo(cart.store) : null);
    }*/
    //?} else {
    /*/^* BuildCraft's own entity item lookup (FabricEntityItems), from the game-test mod's initializer. ^/
    public static void registerLookup() {
        buildcraft.platform.fabric.FabricEntityItems.LOOKUP.registerForType((entity, ctx) ->
                entity instanceof CapabilityOnlyCart cart
                        ? buildcraft.platform.fabric.FabricItemStorages.toFabric(cart.store) : null, minecartType());
    }*/
    //?}
}
