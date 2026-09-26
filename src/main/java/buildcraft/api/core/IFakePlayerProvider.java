/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 * <p>
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.core;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Hands out BuildCraft's fake players. The players are CACHED: one instance per (level, profile), shared by every
 * caller, which is why each method returns it reset to the state of a freshly constructed {@link FakePlayer}
 * (empty inventory, hands and equipment, default rotations and flags, no item in use or cooldowns), placed on the
 * requested block or back where it was created.
 * <p>
 * Contract for callers:
 * <ul>
 * <li>Use the player only within the current method context. Never store it: the next fetch — from any caller —
 * resets it, and it holds a reference to its level (the cache drops it when the level unloads).</li>
 * <li>Do not keep using it across a call that could fetch a fake player again (a nested fetch for the same level and
 * profile resets it under you). The players handed out here are never the ones BuildCraft's own machines use, so
 * BuildCraft never resets a player you hold and you never reset one BuildCraft is using; other API callers share
 * yours.</li>
 * <li>Do not leave live stacks on it: clear what you put in its hands or inventory before returning, so no stack
 * reference outlives your operation.</li>
 * <li>Call only from the server thread.</li>
 * </ul>
 */
public interface IFakePlayerProvider {
    /**
     * Returns the generic "[BuildCraft]" fake player. Prefer the owner-aware
     * {@link #getFakePlayer(ServerLevel, GameProfile)} variants when a real player's UUID is available — this generic
     * player is used as a fallback for code paths (worldgen, springs, robots) that legitimately have no associated
     * user.
     */
    FakePlayer getBuildCraftPlayer(ServerLevel world);

    /**
     * @param world
     * @param profile The owner's profile.
     * @return The owner's cached fake player, reset, at the position it was created at. Use IN THE CURRENT METHOD
     * CONTEXT ONLY — see the interface contract.
     */
    FakePlayer getFakePlayer(ServerLevel world, GameProfile profile);

    /**
     * @param world
     * @param profile The owner's profile.
     * @param pos
     * @return The owner's cached fake player, reset and centred on {@code pos}. Use IN THE CURRENT METHOD CONTEXT
     * ONLY — see the interface contract.
     */
    FakePlayer getFakePlayer(ServerLevel world, GameProfile profile, BlockPos pos);
}
