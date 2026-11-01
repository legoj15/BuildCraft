/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

//? if <1.21.10 {
/*import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;*/
//?}

/**
 * The Robot Goggles: a head-slot item that shows what BuildCraft keeps out of sight. Robots are outlined through walls,
 * red to green by charge (see {@code RobotChargeView}), and the cells a stalled Builder is skipping are outlined
 * (see {@code StallHighlights}). Both ask {@link #isWornBy}, so what counts as "wearing goggles" is decided here.
 *
 * <p>No armour, no durability, no enchantments, and no worn model or equipment asset: the head-slot behaviour is all
 * the item has. {@code BCCoreItems} gives the head slot to {@code >=1.21.10} through the equippable component; 1.21.1
 * has no such component, so the item is an {@code Equipable} there instead and does the right-click swap and the
 * dispenser registration by hand, as vanilla's elytra does.
 */
//? if >=1.21.10 {
public class ItemGoggles extends Item {
    public ItemGoggles(Item.Properties properties) {
        super(properties);
    }
//?} else {
/*public class ItemGoggles extends Item implements Equipable {
    public ItemGoggles(Item.Properties properties) {
        super(properties);
        DispenserBlock.registerBehavior(this, ArmorItem.DISPENSE_ITEM_BEHAVIOR);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }*/
//?}

    /** Whether {@code entity} has Robot Goggles on its head. The one definition: every goggles-only view asks it. */
    public static boolean isWornBy(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ItemGoggles;
    }
}
