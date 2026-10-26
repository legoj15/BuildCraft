/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import buildcraft.core.BCCoreItems;

/**
 * Zombies sometimes carry a Basic Wrench, and sometimes drop one outright. Both apply to the zombies vanilla hands an
 * iron sword or shovel ({@link #CARRIERS}: zombie, husk, zombie villager; drowned carry tridents and are left alone).
 * <ul>
 * <li><b>Carried.</b> A zombie whose spawn was finalized and that vanilla left empty-handed gets a wrench at
 *     vanilla's own gate for its iron weapon (1% on easy and normal, 5% on hard). It is ordinary held equipment, so
 *     vanilla's death-drop code does the rest: the usual 8.5% chance on a player kill, raised by Looting, and a
 *     random wear on the dropped wrench. Nothing here drops it.
 *     NeoForge has no event after {@code Mob#finalizeSpawn}, where vanilla fills the hands, so the finalize event
 *     only marks the mob and the join that follows it is where an empty hand is seen. A mob that joins without
 *     having been finalized (loaded from disk, or added by code that never finalized it) is never given one.</li>
 * <li><b>Dropped.</b> A zombie killed by a player drops a wrench of its own at a flat 1% chance, raised by Looting
 *     the way vanilla raises an equipment drop ({@link EnchantmentHelper#processEquipmentDropChance}), and worn the
 *     way vanilla wears a dropped weapon. This is a drop event rather than a loot table entry because the loot
 *     table formats differ in every line from 1.21.1 to 26.3 and each of them would need its own down-port.</li>
 * </ul>
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public final class ZombieWrenches {

    /** The zombies that may carry or drop a wrench: {@code data/buildcraftunofficial/tags/entity_type/wrench_carriers.json}. */
    public static final TagKey<EntityType<?>> CARRIERS =
        TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("buildcraftunofficial:wrench_carriers"));

    /** Vanilla's gate for a zombie's iron weapon ({@code Zombie#populateDefaultEquipmentSlots}), reused for the wrench. */
    static final float SPAWN_CHANCE = 0.01F;
    static final float SPAWN_CHANCE_HARD = 0.05F;
    /** The base chance of a wrench dropped outright on a player kill, before Looting. */
    static final float DROP_CHANCE = 0.01F;

    /** Carriers whose spawn was finalized and that have not joined a level yet. Finalizing can run off the server thread. */
    private static final Set<Mob> FINALIZED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private ZombieWrenches() {
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (isCarrier(mob) && !event.isCanceled() && !isConversion(event)) {
            FINALIZED.add(mob);
        }
    }

    /** Whether the mob's type is in {@link #CARRIERS}. The type's own tag test is not on every line; the registry's holder is. */
    static boolean isCarrier(LivingEntity mob) {
        return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(mob.getType()).is(CARRIERS);
    }

    /** A converted mob keeps the gear of the one it was made from, and vanilla leaves its hands alone. */
    private static boolean isConversion(FinalizeSpawnEvent event) {
        //? if >=1.21.10 {
        return event.getSpawnType() == net.minecraft.world.entity.EntitySpawnReason.CONVERSION;
        //?} else {
        /*return event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.CONVERSION;*/
        //?}
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (FINALIZED.remove(mob) && mob.getMainHandItem().isEmpty()) {
            giveSpawnWrench(mob, mob.getRandom());
        }
    }

    /** Rolls vanilla's iron-weapon chance and, on a hit, puts a wrench in the mob's main hand. */
    static boolean giveSpawnWrench(Mob mob, RandomSource random) {
        float chance = mob.level().getDifficulty() == Difficulty.HARD ? SPAWN_CHANCE_HARD : SPAWN_CHANCE;
        if (random.nextFloat() >= chance) {
            return false;
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(BCCoreItems.WRENCH.get()));
        return true;
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        // A zombie carrying a wrench already drops it as vanilla equipment; one kill yields at most one.
        if (!event.isRecentlyHit() || !(victim.level() instanceof ServerLevel level) || !isCarrier(victim)
            || victim.getMainHandItem().getItem() instanceof ItemWrench_Neptune) {
            return;
        }
        RandomSource random = victim.getRandom();
        float chance = equipmentDropChance(level, victim, event.getSource());
        if (random.nextFloat() >= chance) {
            return;
        }
        ItemStack wrench = new ItemStack(BCCoreItems.WRENCH.get());
        wearLikeDroppedEquipment(wrench, random);
        ItemEntity drop = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), wrench);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    /**
     * {@link #DROP_CHANCE} after the killer's Looting and any other equipment-drop effects, as vanilla applies them to a
     * held item. Only the overload without a slot exists on every line (1.21.1, 26.1 and 26.3 deprecate it for one with
     * a slot that 1.21.10, 1.21.11 and 26.2 do not have); a drop that belongs to no slot has nothing to pass.
     */
    @SuppressWarnings("deprecation")
    private static float equipmentDropChance(ServerLevel level, LivingEntity victim, DamageSource source) {
        return EnchantmentHelper.processEquipmentDropChance(level, victim, source, DROP_CHANCE);
    }

    /** Vanilla's wear on a mob's dropped equipment ({@code Mob#dropCustomDeathLoot}): usually well used, never new. */
    static void wearLikeDroppedEquipment(ItemStack stack, RandomSource random) {
        int max = stack.getMaxDamage();
        stack.setDamageValue(max - random.nextInt(1 + random.nextInt(Math.max(max - 3, 1))));
    }
}
