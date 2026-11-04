/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The loader's fake players: server players that stand in for a machine. Every player made here is the loader's own
 * fake-player class, so other mods recognise it as a fake player the way they recognise any other. Nothing is cached
 * here; caching and resetting is BuildCraft's ({@code FakePlayerUtil}).
 *
 * <p>This is a contract (see the package documentation): JDK and vanilla types only.
 */
public interface FakePlayers {

    /** A new fake player for {@code profile}, kept by nobody: a throwaway. */
    ServerPlayer create(ServerLevel level, GameProfile profile);

    /** A new fake player for {@code profile} that can also undo what a caller did to it quietly, for a cache that
     *  hands one player out many times. */
    Reusable createReusable(ServerLevel level, GameProfile profile);

    /** Whether {@code entity} is a fake player, made here or by any other mod. */
    boolean isFakePlayer(Entity entity);

    /**
     * A fake player that can put state a caller changed back to a fresh player's without running any hook or
     * emitting any game event: writes to fields only a subclass of the player can reach.
     */
    interface Reusable {
        /** The player itself. */
        ServerPlayer player();

        /** Ends an item use the way a discarded player ends one: no stop-using hook, no game event. */
        void dropUseItemQuietly();

        /** Puts hunger, the hurt and invulnerability timers, death, freezing and portal travel back to a fresh
         *  player's. */
        void resetLivingState();
    }
}
