/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.entity.FakePlayer;

import buildcraft.platform.FakePlayers;

/^*
 * {@link FakePlayers} on Fabric: Fabric API's {@link FakePlayer}, subclassed rather than fetched through
 * {@code FakePlayer.get}, whose one global map is shared with every mod (BuildCraft keeps its own cache).
 *
 * <p><b>Advancement tracker.</b> Vanilla's {@code ServerPlayer} constructor asks the player list for the tracker of
 * the profile's UUID, which re-points the owner's cached tracker at the asker. Nothing is needed here to stop a
 * machine taking an online owner's tracker over: Fabric API's {@code PlayerAdvancementsMixin} (module
 * fabric-events-interaction-v0, read on 1.21.1 and 26.3; the class and target names differ, the guard is the same) cancels {@code PlayerAdvancements.setPlayer} when
 * the new player is a {@link FakePlayer}. That is a reason to stay on {@link FakePlayer}: a machine that did not
 * extend it would need the re-ask {@code ForgeFakePlayers} makes. {@code fake_player_keeps_online_owners_tracker}
 * pins the outcome.
 *^/
public final class FabricFakePlayers implements FakePlayers {

    @Override
    public ServerPlayer create(ServerLevel level, GameProfile profile) {
        return new Throwaway(level, profile);
    }

    @Override
    public Reusable createReusable(ServerLevel level, GameProfile profile) {
        return new ReusableFakePlayer(level, profile);
    }

    @Override
    public boolean isFakePlayer(Entity entity) {
        return entity instanceof FakePlayer;
    }

    /^* Fabric's constructor is protected: a plain subclass, nothing added. *^/
    private static final class Throwaway extends FakePlayer {
        Throwaway(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }
    }

    /^* A plain {@link FakePlayer} that can end an item use and reset its living state quietly (see the contract). *^/
    private static final class ReusableFakePlayer extends FakePlayer implements Reusable {
        ReusableFakePlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public ServerPlayer player() {
            return this;
        }

        @Override
        public void dropUseItemQuietly() {
            setLivingEntityFlag(1, false); // using an item
            setLivingEntityFlag(2, false); // ...with the off hand
            useItem = ItemStack.EMPTY;
            useItemRemaining = 0;
            //? if >=1.21.11 {
            recentKineticEnemies = null;
            //?}
        }

        @Override
        public void resetLivingState() {
            foodData = new FoodData();
            dead = false;
            deathTime = 0;
            hurtTime = 0;
            hurtDuration = 0;
            //? if >=26.3 {
            setInvulnerableTime(0);
            //?} else {
            /^invulnerableTime = 0;
            ^///?}
            setTicksFrozen(0);
            setPortalCooldown(0);
            portalProcess = null;
        }
    }
}
*///?}
