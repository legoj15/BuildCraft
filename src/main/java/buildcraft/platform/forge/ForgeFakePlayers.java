/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.forge;

//? if forge {
/*import java.util.OptionalInt;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import io.netty.channel.ChannelFutureListener;

import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

import buildcraft.platform.FakePlayers;

/^*
 * {@link FakePlayers} on MinecraftForge, which has no fake-player class on 26.3 (nor on 1.21.1): BuildCraft's own
 * {@link MachinePlayer}, a {@link ServerPlayer} on a connection that goes nowhere, built directly (there is no shared
 * fake-player cache to avoid either; BuildCraft keeps its own). Forge's player hooks fire for it as for any player.
 *
 * <p><b>Gap:</b> {@link #isFakePlayer} is true only for players made here. Another mod's fake player has no Forge
 * base class to be recognised by, where NeoForge and Fabric share one.
 *
 * <p><b>Gap (as on Fabric):</b> vanilla's {@code ServerPlayer} constructor asks the player list for the advancement
 * tracker of the profile's UUID, so a machine acting for an owner shares the owner's tracker. BuildCraft never awards
 * through a fake player's own tracker for that reason ({@code AdvancementUtil}); NeoForge's per-line patch that hands a
 * fake player a no-op one is not reproduced.
 *
 * <p><b>Game mode.</b> A machine is always in survival, whatever the server's default is (NeoForge's patch and Fabric
 * API's mixin do the same): {@code ServerPlayerMixin} answers survival where the player constructor asks which mode a
 * new player takes. Left alone, a creative-default server ran every machine in creative: no tool wear, no item used up.
 *^/
public final class ForgeFakePlayers implements FakePlayers {

    @Override
    public ServerPlayer create(ServerLevel level, GameProfile profile) {
        return new MachinePlayer(level, profile);
    }

    @Override
    public Reusable createReusable(ServerLevel level, GameProfile profile) {
        return new ReusableFakePlayer(level, profile);
    }

    @Override
    public boolean isFakePlayer(Entity entity) {
        return isMachinePlayer(entity);
    }

    /^* Whether {@code entity} is a player made here ({@code ServerPlayerMixin} asks it of a player still being constructed). ^/
    public static boolean isMachinePlayer(Object entity) {
        return entity instanceof MachinePlayer;
    }

    /^*
     * A server player that stands in for a machine: never ticked or networked, invulnerable, and unable to hurt, ride,
     * open a menu, hear a message or earn a statistic. Every override is a no-op for a player nobody is connected to,
     * not a behaviour change a machine could notice.
     ^/
    private static class MachinePlayer extends ServerPlayer {
        MachinePlayer(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile, ClientInformation.createDefault());
            this.connection = new NoNetworkListener(level.getServer(), this);
            // The vanilla constructor just asked the player list for this profile's advancement tracker, and the list
            // answers by RE-POINTING the cached tracker at the player who asked. For a machine acting as an online
            // owner that is the owner's live tracker, whose rewards would from then on be granted to this dead
            // player. NeoForge's player list skips fake players before that lookup; Forge has no such bypass, so ask
            // again for the real player, which points the tracker back.
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(getUUID());
            if (online != null && online != this) {
                level.getServer().getPlayerList().getPlayerAdvancements(online);
            }
            setPermanentlyInvulnerable(true);
        }

        @Override
        public void sendSystemMessage(Component message, boolean actionBar) {}

        @Override
        public void awardStat(Stat<?> stat, int amount) {}

        @Override
        public boolean canHarmPlayer(Player player) {
            return false;
        }

        @Override
        public void die(DamageSource source) {}

        @Override
        public void tick() {}

        @Override
        public OptionalInt openMenu(MenuProvider menuProvider) {
            return OptionalInt.empty();
        }

        // Forge's own opener (the one BuildCraft's menus go through on this loader), which would build the menu and send
        // it to the dead connection.
        @Override
        public void openMenu(MenuProvider menuProvider, Consumer<FriendlyByteBuf> extraDataWriter) {}

        @Override
        public void openHorseInventory(AbstractHorse horse, Container container) {}

        @Override
        public boolean startRiding(Entity entityToRide, boolean force, boolean sendEventAndTriggers) {
            return false;
        }
    }

    /^* A {@link MachinePlayer} that can end an item use and reset its living state quietly (see the contract). ^/
    private static final class ReusableFakePlayer extends MachinePlayer implements Reusable {
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
            recentKineticEnemies = null;
        }

        /^* Plain field writes: no event fires. ^/
        @Override
        public void resetLivingState() {
            foodData = new FoodData();
            dead = false;
            deathTime = 0;
            hurtTime = 0;
            hurtDuration = 0;
            setInvulnerableTime(0);
            setTicksFrozen(0);
            setPortalCooldown(0);
            portalProcess = null;
        }
    }

    /^* The listener a {@link MachinePlayer} holds: it sends nothing and has nobody to disconnect. ^/
    private static final class NoNetworkListener extends ServerGamePacketListenerImpl {
        // No channel behind it, shared by every machine player: the sends that would queue on it are overridden away below.
        private static final Connection DEAD_CONNECTION = new Connection(PacketFlow.SERVERBOUND);

        NoNetworkListener(MinecraftServer server, ServerPlayer player) {
            super(server, DEAD_CONNECTION, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void tick() {}

        @Override
        public void resetPosition() {}

        @Override
        public void disconnect(Component message) {}

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, ChannelFutureListener sendListener) {}
    }
}
*///?}
