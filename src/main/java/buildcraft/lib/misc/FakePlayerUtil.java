/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.LevelEvent;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.IFakePlayerProvider;

/**
 * BuildCraft's fake players: one cached {@link FakePlayer} per (level, profile id + name), handed out in a
 * fresh-equivalent state. {@link #PROVIDER} is what {@code BuildCraftAPI.fakePlayerProvider} points at.
 *
 * <h2>Why cache</h2>
 * Constructing a {@code ServerPlayer} is not cheap — on 1.21.1 the constructor runs a spawn-point search and
 * builds a fresh advancements tracker (reading {@code _fake.json}) every time — and the protection check
 * ({@link BlockUtil#canMachineBreak}) fetches one per block inside quarry / mining-well / builder scan loops.
 *
 * <h2>Why a private cache and not {@code FakePlayerFactory}</h2>
 * NeoForge's {@code FakePlayerFactory.get(level, profile)} is one global map shared with every mod: a player keyed
 * on a machine owner's profile would be the SAME instance another mod gets for that owner, so a cross-mod nested
 * fetch (a protection mod's break listener, another mod's placement hook) could mutate it mid-operation. Its key
 * is also the whole {@code GameProfile} — on 1.21.10+ a record whose equality includes the texture properties, so
 * a live owner profile and the same owner re-read from NBT would be two players. This cache is keyed on id + name,
 * is BuildCraft-only, and is emptied on {@link LevelEvent.Unload} exactly as the factory's own map is.
 *
 * <h2>Fresh-equivalent on every fetch</h2>
 * A cached player is shared state, so every fetch first undoes whatever the previous caller left behind:
 * inventory, both hands and all equipment (incl. the 1.21.10+ split-off equipment table), the selected slot, the
 * cursor and 2x2 crafting stacks, an item in use, item cooldowns, rotations (incl. head/body and previous-tick),
 * ground/shift/sprint/swim flags and pose, motion, fall distance, fire, air, effects and health — and puts the
 * player either on the requested block (centre, like the old per-call constructor did) or back where it was
 * created. Resetting only drops references; the previous caller's stacks are never shrunk or emptied.
 *
 * <h2>Leases and reentrancy</h2>
 * BuildCraft's own callers use {@link #lease}: try-with-resources, and {@link Lease#close()} scrubs the items the
 * caller stashed (so no live stack reference outlives the operation). While a lease is open its player is
 * reserved: a nested fetch for the same (level, profile) — from BuildCraft or from a third party through the API —
 * gets a detached, uncached {@code FakePlayer} instead of resetting the outer caller's player under it. Audit at
 * time of writing: no BuildCraft path fetches a fake player while another fetch is in use (the break-permission
 * probe, template placement, stripes pipe drops and the robot AIs all run from their own tick and never
 * synchronously reach another fetch), so the reservation is a guard against third-party listeners and future code.
 *
 * <h2>Threading</h2>
 * The cache is a plain {@link HashMap}; every BuildCraft caller runs on the server thread (block-entity, entity
 * and pipe-flow ticks). An off-thread fetch — only possible from a misbehaving addon — gets a detached player,
 * matching the old always-new behaviour, and is logged once.
 */
@EventBusSubscriber(modid = "buildcraftunofficial")
public final class FakePlayerUtil {

    /**
     * The unowned "[BuildCraft]" machine identity, for work with no recorded owner (worldgen, set-block placed
     * machines, robots). Server operators whitelist it in protection mods by this UUID or name — never change it.
     */
    public static final GameProfile BUILDCRAFT_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("BuildCraft".getBytes(StandardCharsets.UTF_8)), "[BuildCraft]");

    /** The implementation behind {@code BuildCraftAPI.fakePlayerProvider}; see {@link IFakePlayerProvider}. */
    public static final IFakePlayerProvider PROVIDER = new IFakePlayerProvider() {
        @Override
        public FakePlayer getBuildCraftPlayer(ServerLevel world) {
            return fetch(world, null, null);
        }

        @Override
        public FakePlayer getFakePlayer(ServerLevel world, GameProfile profile) {
            return fetch(world, profile, null);
        }

        @Override
        public FakePlayer getFakePlayer(ServerLevel world, GameProfile profile, BlockPos pos) {
            return fetch(world, profile, pos);
        }
    };

    private record Key(ServerLevel level, UUID id, @Nullable String name) {}

    /** A cached player plus the state it was created in, which every fetch restores. */
    private static final class Entry {
        final FakePlayer player;
        final Vec3 homePos;
        final float homeYRot, homeXRot, homeYHeadRot, homeYBodyRot;
        final int homeFireTicks;
        boolean leased;

        Entry(FakePlayer player) {
            this.player = player;
            this.homePos = player.position();
            this.homeYRot = player.getYRot();
            this.homeXRot = player.getXRot();
            this.homeYHeadRot = player.getYHeadRot();
            this.homeYBodyRot = player.yBodyRot;
            this.homeFireTicks = player.getRemainingFireTicks();
        }
    }

    private static final Map<Key, Entry> CACHE = new HashMap<>();
    private static boolean warnedOffThread = false;

    private FakePlayerUtil() {}

    /** {@code profile}, or {@link #BUILDCRAFT_PROFILE} when there is no usable owner profile. */
    public static GameProfile resolveProfile(@Nullable GameProfile profile) {
        return profile == null || GameProfileUtil.getId(profile) == null ? BUILDCRAFT_PROFILE : profile;
    }

    /** Leases the generic "[BuildCraft]" player, left where it was created. */
    public static Lease lease(ServerLevel level) {
        return lease(level, null, null);
    }

    /**
     * Leases the fake player for {@code profile} (the "[BuildCraft]" player when null), reset to a fresh-equivalent
     * state and centred on {@code pos} when one is given. Use with try-with-resources; the player must not be used
     * after {@link Lease#close()}.
     */
    public static Lease lease(ServerLevel level, @Nullable GameProfile profile, @Nullable BlockPos pos) {
        Entry entry = entryFor(level, profile);
        if (entry == null || entry.leased) {
            return new Lease(detached(level, profile, pos), null);
        }
        reset(entry, pos);
        entry.leased = true;
        return new Lease(entry.player, entry);
    }

    private static FakePlayer fetch(ServerLevel level, @Nullable GameProfile profile, @Nullable BlockPos pos) {
        Entry entry = entryFor(level, profile);
        if (entry == null || entry.leased) {
            return detached(level, profile, pos);
        }
        reset(entry, pos);
        return entry.player;
    }

    /** The cached entry, created on first use; null when called off the server thread. */
    @Nullable
    private static Entry entryFor(ServerLevel level, @Nullable GameProfile profile) {
        if (!level.getServer().isSameThread()) {
            if (!warnedOffThread) {
                warnedOffThread = true;
                BCLog.logger.warn("[lib.fakeplayer] A BuildCraft fake player was requested off the server thread;"
                        + " handing out an uncached player. This is a bug in the caller.", new Throwable());
            }
            return null;
        }
        GameProfile resolved = resolveProfile(profile);
        Key key = new Key(level, GameProfileUtil.getId(resolved), GameProfileUtil.getName(resolved));
        return CACHE.computeIfAbsent(key, k -> new Entry(new FakePlayer(level, resolved)));
    }

    private static FakePlayer detached(ServerLevel level, @Nullable GameProfile profile, @Nullable BlockPos pos) {
        FakePlayer player = new FakePlayer(level, resolveProfile(profile));
        if (pos != null) {
            placeAt(player, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }
        return player;
    }

    private static void reset(Entry entry, @Nullable BlockPos pos) {
        FakePlayer player = entry.player;
        scrubItems(player);
        player.setShiftKeyDown(false);
        player.setSprinting(false);
        player.setSwimming(false);
        player.setPose(Pose.STANDING);
        player.setOnGround(false);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.setRemainingFireTicks(entry.homeFireTicks);
        player.setAirSupply(player.getMaxAirSupply());
        if (!player.getActiveEffects().isEmpty()) {
            player.removeAllEffects();
        }
        if (player.getHealth() != player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        placeAt(player, pos == null ? entry.homePos : new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        player.setYRot(entry.homeYRot);
        player.yRotO = entry.homeYRot;
        player.setXRot(entry.homeXRot);
        player.xRotO = entry.homeXRot;
        player.setYHeadRot(entry.homeYHeadRot);
        player.yHeadRotO = entry.homeYHeadRot;
        player.setYBodyRot(entry.homeYBodyRot);
        player.yBodyRotO = entry.homeYBodyRot;
    }

    /** Moves the player and its previous-tick position together, so nothing interpolates from a stale spot. */
    private static void placeAt(FakePlayer player, Vec3 at) {
        player.setPos(at.x, at.y, at.z);
        player.xo = player.xOld = at.x;
        player.yo = player.yOld = at.y;
        player.zo = player.zOld = at.z;
    }

    /**
     * Drops every item reference the player holds: inventory, hands, equipment, selected slot, cursor, 2x2 grid,
     * the item in use, and cooldowns. The stacks themselves are never modified.
     */
    private static void scrubItems(FakePlayer player) {
        if (player.isUsingItem() || !player.getUseItem().isEmpty()) {
            player.stopUsingItem();
        }
        // Covers the hands and all armor/equipment on every node: 1.21.1 keeps them as Inventory compartments,
        // 1.21.10+ clears the split-off EntityEquipment table from the same call.
        player.getInventory().clearContent();
        //? if >=1.21.10 {
        player.getInventory().setSelectedSlot(0);
        //?} else {
        /*player.getInventory().selected = 0;*/
        //?}
        player.inventoryMenu.getCraftSlots().clearContent();
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        // A never-ticked player's cooldowns never expire; a fresh player has none (field opened by the AT).
        player.getCooldowns().cooldowns.clear();
    }

    /** Forgets every cached player of an unloading level, so none keeps the level alive. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CACHE.keySet().removeIf(key -> key.level() == level);
        }
    }

    /**
     * A reserved fake player. {@link #close()} scrubs the items the caller left on it and releases the
     * reservation; closing twice is harmless.
     */
    public static final class Lease implements AutoCloseable {
        private final FakePlayer player;
        @Nullable
        private final Entry entry;
        private boolean closed;

        private Lease(FakePlayer player, @Nullable Entry entry) {
            this.player = player;
            this.entry = entry;
        }

        public FakePlayer player() {
            return player;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            scrubItems(player);
            if (entry != null) {
                entry.leased = false;
            }
        }
    }
}
