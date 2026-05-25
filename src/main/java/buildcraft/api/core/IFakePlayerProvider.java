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

public interface IFakePlayerProvider {
    /**
     * Returns the generic buildcraft fake player. Prefer the owner-aware {@link #getFakePlayer(ServerLevel, GameProfile)}
     * variants when a real player's UUID is available — this generic player is used as a fallback for code paths
     * (worldgen, springs) that legitimately have no associated user.
     */
    FakePlayer getBuildCraftPlayer(ServerLevel world);

    /**
     * @param world
     * @param profile The owner's profile.
     * @return A fake player that can be used IN THE CURRENT METHOD CONTEXT ONLY! This will cause problems if this
     * player is left around as it holds a reference to the world object.
     */
    FakePlayer getFakePlayer(ServerLevel world, GameProfile profile);

    /**
     * @param world
     * @param profile The owner's profile.
     * @param pos
     * @return A fake player that can be used IN THE CURRENT METHOD CONTEXT ONLY! This will cause problems if this
     * player is left around as it holds a reference to the world object.
     */
    FakePlayer getFakePlayer(ServerLevel world, GameProfile profile, BlockPos pos);
}

