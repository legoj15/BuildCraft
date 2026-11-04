/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.neoforge;

//? if neoforge {
import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.platform.FakePlayers;

/**
 * {@link FakePlayers} on NeoForge: plain {@link FakePlayer}s, built directly rather than through
 * {@code FakePlayerFactory}, whose one global map is shared with every mod (BuildCraft keeps its own cache).
 */
public final class NeoForgeFakePlayers implements FakePlayers {

    @Override
    public ServerPlayer create(ServerLevel level, GameProfile profile) {
        return new FakePlayer(level, profile);
    }

    @Override
    public Reusable createReusable(ServerLevel level, GameProfile profile) {
        return new ReusableFakePlayer(level, profile);
    }

    @Override
    public boolean isFakePlayer(Entity entity) {
        return entity instanceof FakePlayer;
    }

    /**
     * A plain {@link FakePlayer} (still {@code instanceof FakePlayer} and {@code isFakePlayer()} for every mod) that
     * can end an item use quietly. {@code stopUsingItem()} would fire the item's stop-using hook and an
     * {@code ITEM_INTERACT_FINISH} vibration that sculk sensors hear; throwaway players are just discarded mid-use and
     * never do either.
     */
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

        /** Plain field writes: no event fires. */
        @Override
        public void resetLivingState() {
            foodData = new FoodData();
            dead = false;
            deathTime = 0;
            hurtTime = 0;
            hurtDuration = 0;
            //? if >=26.3 {
            // the field went private in 26.3
            setInvulnerableTime(0);
            //?} else {
            /*invulnerableTime = 0;
            *///?}
            setTicksFrozen(0);
            setPortalCooldown(0);
            portalProcess = null;
        }
    }
}
//?}
