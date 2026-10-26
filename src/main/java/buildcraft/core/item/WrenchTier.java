/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
//? if >=1.21.10 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Weapon;
//?} else {
/*import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;*/
//?}

/**
 * What a wrench is made of: the vanilla tool material that gives it durability, enchantability, repair item and
 * mining speed, plus the one light-weapon profile every wrench shares.
 * <ul>
 * <li>{@link #IRON}: the Basic and the Marker Wrench (250 uses, enchantability 14, mended with iron ingots, mines
 *     at 6.0).</li>
 * <li>{@link #DIAMOND}: the Volume Wrench (1561 uses, enchantability 10, mended with diamonds, mines at 8.0).</li>
 * </ul>
 * A wrench mines only what {@link #MINEABLE} lists (BuildCraft's machines, pipes, engines, tanks and the like) at its
 * tier's speed, and counts as the right tool for their drops; every other block takes hand speed. Mining a block
 * costs one use, as a pickaxe's does. Wrenching (everything that reaches
 * {@link buildcraft.api.tools.IToolWrench#wrenchUsed}) and a hit on a mob cost one use each; linking markers and
 * lifting, dragging or confirming a volume box cost none.
 * <p>
 * The tool, weapon and tier components are what 1.21.10 and later build from a {@code ToolMaterial}; 1.21.1 has no
 * such helper, so there the same set is made from a {@code Tier} and the item itself answers the enchantability,
 * repair and per-hit questions ({@link ItemWrench_Neptune}).
 */
public enum WrenchTier {
    IRON,
    DIAMOND;

    /** The blocks a wrench mines quickly and drops properly: {@code data/buildcraftunofficial/tags/block/mineable/wrench.json}. */
    public static final TagKey<Block> MINEABLE =
        TagKey.create(Registries.BLOCK, Identifier.parse("buildcraftunofficial:mineable/wrench"));

    /** The attack damage tooltip's total, which includes the player's own 1.0 base. */
    private static final float ATTACK_DAMAGE = 3.5F;
    /** The attack speed tooltip's total, which includes the player's own 4.0 base: the 1.2 a pickaxe swings at. */
    private static final float ATTACK_SPEED = 1.2F;
    private static final float PLAYER_BASE_ATTACK_DAMAGE = 1.0F;
    private static final float PLAYER_BASE_ATTACK_SPEED = 4.0F;

    //? if >=1.21.10 {
    private ToolMaterial material() {
        return this == IRON ? ToolMaterial.IRON : ToolMaterial.DIAMOND;
    }

    /**
     * Adds durability, enchantability, the repair item, the mining rules, the attack attributes and the one-use
     * hit cost to {@code properties}. The vanilla helper sets a hit cost of 2 (a tool's), so it is lowered to 1.
     */
    public Item.Properties apply(Item.Properties properties) {
        ToolMaterial material = material();
        return properties
            .tool(material, MINEABLE,
                ATTACK_DAMAGE - PLAYER_BASE_ATTACK_DAMAGE - material.attackDamageBonus(),
                ATTACK_SPEED - PLAYER_BASE_ATTACK_SPEED, 0.0F)
            .component(DataComponents.WEAPON, new Weapon(1, 0.0F));
    }
    //?} else {
    /*private Tier tier() {
        return this == IRON ? Tiers.IRON : Tiers.DIAMOND;
    }

    public Item.Properties apply(Item.Properties properties) {
        Tier tier = tier();
        return properties
            .durability(tier.getUses())
            .component(DataComponents.TOOL, tier.createToolProperties(MINEABLE))
            .attributes(DiggerItem.createAttributes(tier,
                ATTACK_DAMAGE - PLAYER_BASE_ATTACK_DAMAGE - tier.getAttackDamageBonus(),
                ATTACK_SPEED - PLAYER_BASE_ATTACK_SPEED));
    }

    int enchantmentValue() {
        return tier().getEnchantmentValue();
    }

    boolean repairs(ItemStack repairWith) {
        return tier().getRepairIngredient().test(repairWith);
    }*/
    //?}
}
