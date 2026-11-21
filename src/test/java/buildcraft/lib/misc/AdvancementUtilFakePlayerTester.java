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

import buildcraft.platform.Platform;


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
    /** A test-pack advancement (criterion {@code go}, never fired by a trigger) whose reward is {@link #PROBE_XP}. */
    private static final Identifier PROBE_ADVANCEMENT = Identifier.parse("buildcraftunofficial:test_tracker_probe");
    private static final String PROBE_CRITERION = "go";
    private static final int PROBE_XP = 7;

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
        ServerPlayer offlineMachine = Platform.fakePlayers().create(level, new GameProfile(UUID.randomUUID(), "bc_adv_offline"));
        AdvancementUtil.unlockAdvancement(offlineMachine, LIST_ADVANCEMENT);
        helper.assertTrue(!offlineMachine.getAdvancements().getOrStartProgress(holder).isDone(),
                "an offline owner's fake player records nothing");

        // Online owner: the award reaches the owner's own tracker.
        GameProfile ownerProfile = new GameProfile(UUID.randomUUID(), "bc_adv_owner");
        ServerPlayer owner = new ServerPlayer(level.getServer(), level, ownerProfile, ClientInformation.createDefault());
        Map<UUID, ServerPlayer> online = playersByUuid(level.getServer().getPlayerList());
        online.put(owner.getUUID(), owner);
        try {
            ServerPlayer machine = Platform.fakePlayers().create(level, ownerProfile);
            AdvancementUtil.unlockAdvancement(machine, LIST_ADVANCEMENT);
            helper.assertTrue(owner.getAdvancements().getOrStartProgress(holder).isDone(),
                    "an advancement earned by the owner's machine reaches the online owner");
        } finally {
            online.remove(owner.getUUID());
        }
        helper.succeed();
    }

    /**
     * Building a machine player for an owner who is online must leave the owner's advancement tracker pointed at the
     * owner. The vanilla {@code ServerPlayer} constructor asks the player list for the tracker of its profile's UUID,
     * and the list answers by re-pointing the cached tracker at whoever asked; a machine that took it over would
     * collect the owner's advancement rewards (recipe unlocks, experience) and fire the advancement events for a dead
     * entity until the owner relogged. NeoForge's player list never re-points it at a fake player, Fabric API's mixin cancels the
     * re-pointing, and Forge's machine player re-asks for the owner: this pins the outcome on every loader.
     * <p>
     * The reward is read off the owner, never the tracker's own accessor: asking the player list for the tracker of
     * the owner would itself re-point it and mask the bug. Every way a machine is built is covered (the throwaway,
     * the reusable, the cached lease the machines actually run on), and so is the owner's login after a machine was
     * built first.
     */
    public static void machinePlayerLeavesTheOnlineOwnersTracker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AdvancementHolder probe = level.getServer().getAdvancements().get(PROBE_ADVANCEMENT);
        helper.assertTrue(probe != null, "precondition: the test pack's probe advancement is loaded");
        Map<UUID, ServerPlayer> online = playersByUuid(level.getServer().getPlayerList());

        GameProfile ownerProfile = new GameProfile(UUID.randomUUID(), "bc_adv_tracker_owner");
        ServerPlayer owner = new ServerPlayer(level.getServer(), level, ownerProfile, ClientInformation.createDefault());
        online.put(owner.getUUID(), owner);
        try {
            ServerPlayer throwaway = Platform.fakePlayers().create(level, ownerProfile);
            ServerPlayer reusable = Platform.fakePlayers().createReusable(level, ownerProfile).player();
            try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, ownerProfile, null)) {
                helper.assertTrue(lease.player() != owner, "precondition: the leased machine is not the owner");
            }
            helper.assertTrue(owner.getAdvancements().award(probe, PROBE_CRITERION), "the probe criterion was granted");
            helper.assertTrue(owner.totalExperience == PROBE_XP,
                    "the reward of an advancement earned after machines were built reaches the online owner, got "
                            + owner.totalExperience + " experience");
            helper.assertTrue(throwaway.totalExperience == 0 && reusable.totalExperience == 0,
                    "no machine player collected the owner's reward");

            // Discarded machines and a rebuilt cache entry leave it where it was.
            throwaway.discard();
            reusable.discard();
            FakePlayerUtil.onPlayerLoggedOut(owner);
            try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, ownerProfile, null)) {
                helper.assertTrue(lease.player() != owner, "precondition: the rebuilt machine is not the owner");
            }
            owner.getAdvancements().revoke(probe, PROBE_CRITERION);
            helper.assertTrue(owner.getAdvancements().award(probe, PROBE_CRITERION), "the criterion was granted again");
            helper.assertTrue(owner.totalExperience == 2 * PROBE_XP,
                    "the reward still reaches the online owner after the machines were discarded and rebuilt, got "
                            + owner.totalExperience + " experience");
        } finally {
            online.remove(owner.getUUID());
            FakePlayerUtil.onPlayerLoggedOut(owner);
        }

        // The other order: the machine exists before its owner logs in; the login binds the owner's own tracker. This is
        // vanilla's own behaviour (the last constructor wins), kept as a sanity check: no loader's defence is what makes it pass.
        GameProfile laterProfile = new GameProfile(UUID.randomUUID(), "bc_adv_tracker_later");
        ServerPlayer earlyMachine = Platform.fakePlayers().create(level, laterProfile);
        ServerPlayer later = new ServerPlayer(level.getServer(), level, laterProfile, ClientInformation.createDefault());
        online.put(later.getUUID(), later);
        try {
            helper.assertTrue(later.getAdvancements().award(probe, PROBE_CRITERION), "the probe criterion was granted");
            helper.assertTrue(later.totalExperience == PROBE_XP && earlyMachine.totalExperience == 0,
                    "an owner who logs in after its machine was built gets its own rewards, got "
                            + later.totalExperience + " experience");
        } finally {
            online.remove(later.getUUID());
            earlyMachine.discard();
        }
        helper.succeed();
    }
}
