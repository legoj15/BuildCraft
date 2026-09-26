/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.misc;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * An advancement earned through a fake player (a stripes pipe using a list, a machine acting for its owner) belongs
 * to the real player with that UUID: the owner gets it when online, nobody when offline. Before the fix the award
 * went through the fake player's own tracker, which on 1.21.1 and 26.2 is NeoForge's no-op one — the owner never got
 * anything there. (On 1.21.10 - 26.1.x the tracker is shared by UUID, so the owner happened to get it.)
 * <p>
 * The online owner is a real {@link ServerPlayer} made findable by UUID the way a login does, minus the network
 * connection: entered into the player list's UUID index for the duration of the test and removed in a
 * {@code finally}. Posting a real login would run every mod's join logic on the shared game-test server.
 */
public class AdvancementUtilFakePlayerTester {

    private static final Identifier LIST_ADVANCEMENT = Identifier.parse("buildcraftunofficial:list");

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playersByUuid(PlayerList list) {
        try {
            Field field = PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);
            return (Map<UUID, ServerPlayer>) field.get(list);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void fakePlayerAwardsReachTheOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AdvancementHolder holder = level.getServer().getAdvancements().get(LIST_ADVANCEMENT);
        helper.assertTrue(holder != null, "precondition: the list advancement exists");

        // Offline owner: nobody receives it, and the fake player's own tracker is never written.
        FakePlayer offlineMachine = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "bc_adv_offline"));
        AdvancementUtil.unlockAdvancement(offlineMachine, LIST_ADVANCEMENT);
        helper.assertTrue(!offlineMachine.getAdvancements().getOrStartProgress(holder).isDone(),
                "an offline owner's fake player records nothing");

        // Online owner: the award reaches the owner's own tracker.
        GameProfile ownerProfile = new GameProfile(UUID.randomUUID(), "bc_adv_owner");
        ServerPlayer owner = new ServerPlayer(level.getServer(), level, ownerProfile, ClientInformation.createDefault());
        Map<UUID, ServerPlayer> online = playersByUuid(level.getServer().getPlayerList());
        online.put(owner.getUUID(), owner);
        try {
            FakePlayer machine = new FakePlayer(level, ownerProfile);
            AdvancementUtil.unlockAdvancement(machine, LIST_ADVANCEMENT);
            helper.assertTrue(owner.getAdvancements().getOrStartProgress(holder).isDone(),
                    "an advancement earned by the owner's machine reaches the online owner");
        } finally {
            online.remove(owner.getUUID());
        }
        helper.succeed();
    }
}
