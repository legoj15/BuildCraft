/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.item;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.statements.StatementSlot;

/**
 * Homing consent is bound to ownership ({@link ItemRobot#canHomeOn}): once a robot is homed on a station it
 * acts for that station's owner, so only the owner may plant a robot there. These pin the pure decision —
 * the UUID match, the same-name-different-player refusal, the unowned-station allowance, and the
 * faceless-placement refusal — without a level (a station double over a bare {@link DockingStation}).
 */
public class ItemRobotHomingConsentTest {

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    /** A minimal station double whose owner is whatever the test says; {@code getOwner} is the only station
     * side the consent decision reads. */
    private static DockingStation stationOwnedBy(GameProfile owner) {
        return new DockingStation(new BlockPos(0, 64, 0), Direction.UP) {
            @Override
            public GameProfile getOwner() {
                return owner;
            }

            @Override
            public Iterable<StatementSlot> getActiveActions() {
                return java.util.Collections.emptyList();
            }
        };
    }

    @Test
    public void theOwnerMayHomeARobotOnTheirOwnStation() {
        GameProfile owner = profile("alice");
        Assertions.assertTrue(ItemRobot.canHomeOn(owner, stationOwnedBy(owner)),
                "the station's owner may home a robot on it");
    }

    @Test
    public void anotherPlayerMayNot() {
        GameProfile owner = profile("alice");
        Assertions.assertFalse(ItemRobot.canHomeOn(profile("bob"), stationOwnedBy(owner)),
                "a robot homed here would act as alice, so bob may not plant one");
    }

    @Test
    public void aMatchingNameWithADifferentIdMayNot() {
        GameProfile owner = profile("alice");
        GameProfile nameSquatter = new GameProfile(UUID.randomUUID(), "alice");
        Assertions.assertFalse(ItemRobot.canHomeOn(nameSquatter, stationOwnedBy(owner)),
                "consent matches the persisted UUID, never the display name");
    }

    @Test
    public void aFacelessPlacementIsRefusedByAnOwnedStation() {
        Assertions.assertFalse(ItemRobot.canHomeOn(null, stationOwnedBy(profile("alice"))),
                "nobody is vouching for the robot, and an owned station's identity is not grantable anonymously");
    }

    @Test
    public void anOwnerlessStationStillAcceptsAnyone() {
        Assertions.assertTrue(ItemRobot.canHomeOn(profile("bob"), stationOwnedBy(null)),
                "a station nobody owns homes anyone; its robots then ask protection mods as the generic"
                        + " \"[BuildCraft]\" identity, so nothing is bypassed");
    }

    // canHomeOn also treats an id-less owner profile as unowned and refuses an id-less placer against an
    // owned station — defence for the 1.21.1 node, whose authlib 6 still allows a GameProfile with no id.
    // On this node's authlib 7 such a profile cannot be constructed at all ("Profile ID must not be null"),
    // so those two branches are not reachable here and are not tested headlessly.
}
