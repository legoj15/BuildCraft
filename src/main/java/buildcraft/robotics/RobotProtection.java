/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.MachineIdentity;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.tile.OwnerData;

/**
 * Who a robot is when protection mods are asked. A robot answers to two players: whoever deployed it (placed it on a
 * station from an item; {@link IRobotAccess#getDeployer}) and the owner of its home (linked) station, who is whoever
 * placed the pipe the station sits on. Any player may home a robot on any free station (as in 7.1.x), so neither
 * player alone may vouch for what the robot does: it ACTS as its deployer (the leased fake player, so claim mods judge,
 * and audit logs and advancements attribute to, the player who put the robot to work), and every protection question
 * it asks must be answered yes for BOTH the deployer and the station's owner. A robot flying out of its owner's base
 * is refused wherever that owner would be, like the Quarry and Builder, which ask as the player who placed them; and
 * a robot homed on someone else's station is refused wherever its own deployer would be. When the two are the same
 * player (same UUID) the question is asked once: these probes post a real protection event per block inside robot
 * search loops.
 *
 * <p>A robot with no recorded deployer (one saved before deployers were recorded) acts and asks as its station's owner
 * alone, exactly as every robot did before. A robot with no home station (or whose station's owner has no name) has
 * the generic "[BuildCraft]" identity on the station side, as 7.1.x's robots always did; {@link MachineIdentity} is
 * the one rule for that, on both sides. A station remembers its owner (it is saved with the station), so it can name
 * them while its pipe's chunk is not loaded. A station that cannot name them at all
 * ({@link DockingStation#isOwnerKnown} false: saved before stations remembered their owner, pipe not loaded since)
 * fails CLOSED: every guard here refuses, instead of asking as "[BuildCraft]", whom operators whitelist.
 *
 * <p>Three shapes of guard follow from this. A site that asks a probe ({@link #canBreak}) asks it for both identities.
 * A site that acts through the leased player's own events (a planting, a tool use, a stripes handler) gets the
 * deployer's say from those events, and asks the closest probe for the station's owner first
 * ({@link #stationOwnerCanPlace}, {@link #stationOwnerCanBreak}, {@link #stationOwnerShortcut}). A melee swing
 * ({@link #attack}) is asked as a player's attack, for both, and lands as the deployer's; the bomber's TNT is the
 * deployer's, dropped only where {@link #canBreak} allows at the block it will land on, and carries both players
 * ({@link #markBomb}) so its blast takes only what both may break or attack ({@link #onExplosionDetonate}).
 *
 * <p>Two robot actions ask nobody, as in 7.1.x, because a player doing the same asks nobody either: a picker
 * ({@code AIRobotFetchItem}) picking up an item lying loose in the world, and a builder robot
 * ({@code BoardRobotBuilder}) delivering items into any loaded Builder that wants them.
 */
public final class RobotProtection {
    private RobotProtection() {}

    /** Stands for "the home station cannot name its owner" in a refusal memory's key: a distinct, named identity,
     *  so refusals given while the owner was unknown are forgotten the moment it is known, even when the station
     *  turns out to have no owner (which keys as "[BuildCraft]"). Never asked about and never acted as. */
    private static final GameProfile UNKNOWN_OWNER_KEY = new GameProfile(
            UUID.nameUUIDFromBytes("BuildCraft robot station owner unknown".getBytes(StandardCharsets.UTF_8)),
            "[BuildCraft: station owner unknown]");

    /** Where a robot's TNT keeps the two players it answers to, in its persistent data: saved with the entity, so a
     *  blast whose fuse outlives a chunk unload still knows them. */
    private static final String BOMB_TAG = "buildcraftunofficial:robot_bomb";

    /** TNT blocks a robot's blast is taking this tick, with that blast's players, for the TNT each primes (inside the
     *  blast, before the tick ends); cleared every server tick. */
    private static final Map<ServerLevel, Map<BlockPos, CompoundTag>> CHAINED = new WeakHashMap<>();

    /** The robot's home station owner, or null when it has no home station, the station has no owner, or it cannot
     *  name its owner ({@link #stationOwnerUnknown}). */
    @Nullable
    public static GameProfile ownerOf(IRobotAccess robot) {
        DockingStation home = robot.getLinkedStation();
        return home == null ? null : home.getOwner();
    }

    /** Whether the robot's home station cannot name its owner ({@link DockingStation#isOwnerKnown}). A robot with no
     *  home station is not unknown: its station side is "[BuildCraft]", as always. */
    public static boolean stationOwnerUnknown(IRobotAccess robot) {
        DockingStation home = robot.getLinkedStation();
        return home != null && !home.isOwnerKnown();
    }

    /** Whether every protection question this robot would ask is refused outright because its station's owner is
     *  unknown. False while {@link BCCoreConfig#minePlayerProtected} is on: machines then ask nobody at all. */
    private static boolean failsClosed(IRobotAccess robot) {
        return !BCCoreConfig.minePlayerProtected.get() && stationOwnerUnknown(robot);
    }

    /** The owner half of a refusal memory's key ({@link buildcraft.lib.misc.RefusalMemory}, beside
     *  {@link #identityOf}): {@link #ownerOf}, or a stand-in while the owner is unknown, so a cell refused only
     *  because the owner could not be named is asked again as soon as they can be. */
    @Nullable
    public static GameProfile refusalKeyOf(IRobotAccess robot) {
        return stationOwnerUnknown(robot) ? UNKNOWN_OWNER_KEY : ownerOf(robot);
    }

    /** The identity this robot acts as: its deployer when one is recorded, else its home station's owner, each
     *  resolved through {@link MachineIdentity} (an unnamed profile or none is "[BuildCraft]"). */
    public static GameProfile identityOf(IRobotAccess robot) {
        GameProfile deployer = robot.getDeployer();
        return MachineIdentity.of(deployer != null ? deployer : ownerOf(robot));
    }

    /** The station side of the pair: the home station's named owner, else "[BuildCraft]". */
    public static GameProfile stationIdentityOf(IRobotAccess robot) {
        return MachineIdentity.of(ownerOf(robot));
    }

    /** Whether the station's owner is a different player from the one the robot acts as, so it must be asked on its
     *  own. Compared by UUID: a profile's name can change between sessions, and two profile objects can name one
     *  player. */
    public static boolean stationOwnerAsksSeparately(IRobotAccess robot) {
        return !Objects.equals(GameProfileUtil.getId(identityOf(robot)),
                GameProfileUtil.getId(stationIdentityOf(robot)));
    }

    /**
     * Leases the fake player this robot acts as in the world: its deployer's ({@link #identityOf}; the station owner's
     * for a robot with no recorded deployer), so claim mods judge that player and advancements a used item grants reach
     * them. Use with try-with-resources, like every {@link FakePlayerUtil.Lease}. Acting through the lease answers only
     * for that player: a caller first asks the station's owner too, through {@link #canBreak} or a
     * {@code stationOwnerCan*} probe.
     */
    public static FakePlayerUtil.Lease lease(IRobotAccess robot, ServerLevel level) {
        return FakePlayerUtil.lease(level, identityOf(robot), null);
    }

    /**
     * Puts the robot's tool in the leased fake player's main hand, the way a ticking player holds it: vanilla's
     * destroy-speed maths and every claim or tool listener read the player's hand, not the stack an event carries.
     * The lease takes the tool back out of the hand when it closes; the stack is never copied, so wear the use puts
     * on it lands on the robot's own tool.
     */
    public static void holdInMainHand(FakePlayer player, ItemStack tool) {
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        //? if >=1.21.10 {
        // 1.21.10 split the player's equipment table off the Inventory: setItemInHand now writes the table, while
        // Player.getDestroySpeed still reads inventory.getSelectedItem(). Real players bridge the two while
        // ticking; a never-ticked fake player bridges nowhere, and without this line a robot mined at hand speed -
        // the tool's speed never entered the formula at all (found by the Ph9 break-progress game test).
        player.getInventory().setSelectedItem(tool);
        //?}
    }

    /** Whether protection mods let this robot break the block at {@code pos}: they must permit both the identity it
     *  acts as and its home station's owner (asked once when they are the same player); see
     *  {@link BlockUtil#canMachineBreak}. */
    public static boolean canBreak(IRobotAccess robot, ServerLevel level, BlockPos pos) {
        return !failsClosed(robot) && BlockUtil.canMachineBreak(level, pos, identityOf(robot))
                && stationOwnerCanBreak(robot, level, pos);
    }

    /** The station owner's half of a break, for a site whose own events already ask as the acting identity: false
     *  when the owner is unknown, true when the station's owner is that same player, else
     *  {@link BlockUtil#canMachineBreak} for the owner. */
    public static boolean stationOwnerCanBreak(IRobotAccess robot, ServerLevel level, BlockPos pos) {
        return !failsClosed(robot) && (!stationOwnerAsksSeparately(robot)
                || BlockUtil.canMachineBreak(level, pos, stationIdentityOf(robot)));
    }

    /** The station owner's half of setting something down in {@code pos}, for a site whose own events (a planting's
     *  placement, a stripes handler's) already ask as the acting identity: false when the owner is unknown, true when
     *  the station's owner is that same player, else {@link BlockUtil#canMachinePlace} for the owner. */
    public static boolean stationOwnerCanPlace(IRobotAccess robot, ServerLevel level, BlockPos pos) {
        return !failsClosed(robot) && (!stationOwnerAsksSeparately(robot)
                || BlockUtil.canMachinePlace(level, pos, stationIdentityOf(robot)));
    }

    /** The station owner's say for a site that runs its own probes for that owner (the stripes handler's several
     *  cells): FALSE when the owner is unknown, TRUE when the owner is the player the robot acts as (that player's
     *  own events judge the act), else null, meaning "ask {@link #stationIdentityOf} yourself". */
    @Nullable
    public static Boolean stationOwnerShortcut(IRobotAccess robot) {
        if (failsClosed(robot)) {
            return Boolean.FALSE;
        }
        return stationOwnerAsksSeparately(robot) ? null : Boolean.TRUE;
    }

    /**
     * Swings the robot's held item at {@code target}, if protection mods let both its players attack it. The event a
     * player's own swing posts ({@link AttackEntityEvent}, which claim mods' animal and PvE protection listen to) is
     * posted for the deployer's fake player and, when a different player, for the station owner's, each standing where
     * the robot is and holding its weapon ({@link #holdInMainHand}: listeners read the hand); nothing is hit unless
     * neither is cancelled. The hit is then {@link IRobotAccess#attackTargetEntityWithCurrentItem(Entity, Player)}
     * naming the deployer's player as the attacker, still holding the weapon: the hit's damage reads the robot's own
     * stack, but a kill's loot reads Looting off the killer's hand (the loot functions and the equipment drop chance
     * alike), so a robot's kill gets its weapon's Looting, as a player's would (7.1.x's robots never applied it). The
     * event is posted directly rather than through {@code CommonHooks.onPlayerAttackTarget} (the same event, under that
     * name on every node), whose second half would run the held weapon's own left-click hook, which a robot's hit never
     * did. While {@link BCCoreConfig#minePlayerProtected} is on nobody is asked, as for breaks.
     *
     * @return true when the swing went ahead (the hit itself may still do nothing, e.g. to an invulnerable target);
     *         false when a protection mod refused it, or the station's owner is unknown
     */
    public static boolean attack(IRobotAccess robot, ServerLevel level, Entity target) {
        if (failsClosed(robot)) {
            return false;
        }
        boolean ask = !BCCoreConfig.minePlayerProtected.get();
        Vec3 at = robot.position();
        try (FakePlayerUtil.Lease lease = lease(robot, level)) {
            FakePlayer attacker = lease.player();
            FakePlayerUtil.pose(attacker, at, 0, 0);
            holdInMainHand(attacker, robot.getHeldItem());
            if (ask && NeoForge.EVENT_BUS.post(new AttackEntityEvent(attacker, target)).isCanceled()) {
                return false;
            }
            if (ask && stationOwnerAsksSeparately(robot)) {
                try (FakePlayerUtil.Lease ownerLease = FakePlayerUtil.lease(level, stationIdentityOf(robot), null)) {
                    FakePlayer owner = ownerLease.player();
                    FakePlayerUtil.pose(owner, at, 0, 0);
                    holdInMainHand(owner, robot.getHeldItem());
                    if (NeoForge.EVENT_BUS.post(new AttackEntityEvent(owner, target)).isCanceled()) {
                        return false;
                    }
                }
            }
            robot.attackTargetEntityWithCurrentItem(target, attacker);
            return true;
        }
    }

    // ── The bomber's blast ──────────────────────────────────────────────────

    /** Records on {@code tnt} the two players {@code robot} answers to ({@link #identityOf},
     *  {@link #stationIdentityOf}) so that {@link #onExplosionDetonate} can ask both about its blast. */
    public static void markBomb(IRobotAccess robot, PrimedTnt tnt) {
        CompoundTag deployer = new CompoundTag();
        OwnerData.writeOwner(deployer, identityOf(robot));
        CompoundTag stationOwner = new CompoundTag();
        OwnerData.writeOwner(stationOwner, stationIdentityOf(robot));
        CompoundTag players = new CompoundTag();
        players.put("deployer", deployer);
        players.put("stationOwner", stationOwner);
        tnt.getPersistentData().put(BOMB_TAG, players);
    }

    /**
     * Narrows the blast of a robot's TNT ({@link #markBomb}) to what both its players may touch: every block it would
     * destroy must be one each may break ({@link BlockUtil#canMachineBreakAs}), and every entity it would hurt one each
     * may attack (the attack-entity event a melee swing posts), or it is left out of the blast. Claim mods that judge
     * explosions by their igniter see only the deployer (the TNT's owner); without this the station's owner was asked
     * about the landing block alone. Entities the blast would not hurt anyway (out of its reach, or ignoring
     * explosions) are not asked about. A TNT block the blast still takes primes TNT that inherits the mark
     * ({@link #onEntityJoin}), so a chain of blasts is asked about link by link. Nobody is asked while
     * {@link BCCoreConfig#minePlayerProtected} is on.
     */
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || BCCoreConfig.minePlayerProtected.get()) {
            return;
        }
        Explosion explosion = event.getExplosion();
        // Only TNT carries the mark; asking any other source would give it an empty persistent tag that then saves
        if (!(explosion.getDirectSourceEntity() instanceof PrimedTnt source)) {
            return;
        }
        CompoundTag players = NBTUtilBC.getCompoundOrNull(source.getPersistentData(), BOMB_TAG);
        if (players == null) {
            return;
        }
        GameProfile deployer = markedPlayer(players, "deployer");
        GameProfile stationOwner = markedPlayer(players, "stationOwner");
        keepPermitted(level, event, explosion, deployer);
        if (!Objects.equals(GameProfileUtil.getId(deployer), GameProfileUtil.getId(stationOwner))) {
            keepPermitted(level, event, explosion, stationOwner);
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (level.getBlockState(pos).getBlock() instanceof TntBlock) {
                CHAINED.computeIfAbsent(level, l -> new HashMap<>()).put(pos.immutable(), players.copy());
            }
        }
    }

    /** Marks TNT that a robot's blast primed out of a TNT block ({@link #CHAINED}) with the players of that blast. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (CHAINED.isEmpty() || !(event.getEntity() instanceof PrimedTnt tnt)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, CompoundTag> primed = CHAINED.get(level);
        CompoundTag players = primed == null ? null : primed.remove(BlockPos.containing(tnt.position()));
        // Vanilla primes it owned by the blast's igniter, the deployer's player: TNT someone else lit in that cell in
        // the same tick is not the robot's
        if (players != null && tnt.getOwner() != null
                && tnt.getOwner().getUUID().equals(GameProfileUtil.getId(markedPlayer(players, "deployer")))) {
            tnt.getPersistentData().put(BOMB_TAG, players);
        }
    }

    /** One of the two players a {@link #markBomb} mark names ({@code "deployer"} or {@code "stationOwner"}). */
    private static GameProfile markedPlayer(CompoundTag players, String key) {
        return MachineIdentity.of(OwnerData.readOwner(NBTUtilBC.getCompound(players, key)));
    }

    /** Forgets the TNT blocks of this tick's robot blasts: vanilla primes them inside the blast itself. */
    public static void clearChained() {
        CHAINED.clear();
    }

    /** Drops from the blast every block {@code profile} may not break and every entity it would hurt that
     *  {@code profile} may not attack. */
    private static void keepPermitted(ServerLevel level, ExplosionEvent.Detonate event, Explosion explosion,
            GameProfile profile) {
        Vec3 center = explosion.center();
        // Vanilla hurts an entity only within twice the radius; the event's list is the whole box around that
        double reach = explosion.radius() * 2.0;
        try (FakePlayerUtil.Lease lease = FakePlayerUtil.lease(level, profile, null)) {
            FakePlayer player = lease.player();
            event.getAffectedBlocks().removeIf(pos -> !BlockUtil.canMachineBreakAs(level, pos, player));
            FakePlayerUtil.pose(player, center, 0, 0);
            event.getAffectedEntities().removeIf(entity -> !entity.ignoreExplosion(explosion)
                    && entity.distanceToSqr(center) <= reach * reach
                    && NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, entity)).isCanceled());
        }
    }
}
