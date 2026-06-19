/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.api.core;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

/**
 * Version-neutral NBT helpers that live in the API layer, so {@code buildcraft.api} classes never have to reach down
 * into {@code buildcraft.lib}. Third-party mods compiling against the BuildCraft API may use these directly.
 *
 * <p>These intentionally mirror the matching methods in {@code buildcraft.lib.misc.NBTUtilBC} (the implementation-side
 * super-set utility). Keep the two in sync — in particular the Stonecutter {@code //? if >=1.21.10} branches that
 * bridge the 1.21.5+ {@code Optional}-returning {@code CompoundTag} getters to the 1.21.1 direct getters. The
 * {@code >=1.21.10} branch is exactly what BuildCraft already does on the released nodes, so runtime behaviour is
 * unchanged.
 */
public final class NbtApiUtil {

    private NbtApiUtil() {}

    /** Resolve a registry-aware {@link DynamicOps} for NBT codecs that need to look up dynamic-registry entries
     * (enchantments, banner patterns, painting variants, jukebox songs, etc.).
     *
     * <p>Without a {@link RegistryOps} wrapper around {@link NbtOps#INSTANCE}, codecs that reference dynamic registries
     * fail silently — {@code DataResult} returns an error and {@code resultOrPartial()} yields an empty
     * {@code Optional}, so any {@code .ifPresent(payload -> tag.put(...))} call ends up writing nothing.
     *
     * <p>Source priority: integrated/dedicated server's registry access (works in either) → client level's registry
     * access → plain {@link NbtOps#INSTANCE} as a last-resort fallback (lossy, but prevents NPE if invoked before any
     * world is loaded). */
    public static DynamicOps<Tag> registryAwareOps() {
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return RegistryOps.create(NbtOps.INSTANCE, (HolderLookup.Provider) server.registryAccess());
        }
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            HolderLookup.Provider clientProvider = clientLevelRegistryAccess();
            if (clientProvider != null) {
                return RegistryOps.create(NbtOps.INSTANCE, clientProvider);
            }
        }
        return NbtOps.INSTANCE;
    }

    /** Wrapped in its own method so the client-only class reference doesn't get loaded on dedicated servers (which
     * don't have {@code Minecraft} on the classpath at all). */
    private static HolderLookup.Provider clientLevelRegistryAccess() {
        try {
            net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? null : level.registryAccess();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ── Version-neutral CompoundTag accessors (mirror NBTUtilBC) ──

    public static byte getByte(CompoundTag nbt, String key, byte def) {
        //? if >=1.21.10 {
        return nbt.getByteOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getByte(key) : def;*/
        //?}
    }

    public static int getInt(CompoundTag nbt, String key, int def) {
        //? if >=1.21.10 {
        return nbt.getIntOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getInt(key) : def;*/
        //?}
    }

    public static long getLong(CompoundTag nbt, String key, long def) {
        //? if >=1.21.10 {
        return nbt.getLongOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getLong(key) : def;*/
        //?}
    }

    public static boolean getBoolean(CompoundTag nbt, String key, boolean def) {
        //? if >=1.21.10 {
        return nbt.getBooleanOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getBoolean(key) : def;*/
        //?}
    }

    public static String getString(CompoundTag nbt, String key, String def) {
        //? if >=1.21.10 {
        return nbt.getStringOr(key, def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getString(key) : def;*/
        //?}
    }

    public static int[] getIntArray(CompoundTag nbt, String key, int[] def) {
        //? if >=1.21.10 {
        return nbt.getIntArray(key).orElse(def);
        //?} else {
        /*return nbt.contains(key) ? nbt.getIntArray(key) : def;*/
        //?}
    }

    /** CompoundTag child, empty (new) when absent. */
    public static CompoundTag getCompound(CompoundTag nbt, String key) {
        //? if >=1.21.10 {
        return nbt.getCompoundOrEmpty(key);
        //?} else {
        /*return nbt.getCompound(key);*/
        //?}
    }
}
