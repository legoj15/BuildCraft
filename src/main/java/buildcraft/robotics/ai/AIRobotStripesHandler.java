/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.util.FakePlayer;

import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.IRobotAccess;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.pipe.PipeApi;

import buildcraft.lib.misc.BlockUtil;
import buildcraft.lib.misc.FakePlayerUtil;
import buildcraft.lib.misc.MachineRefusals;
import buildcraft.robotics.RobotProtection;

/** The stripes board's item-use cycle: aim the held item at the reserved block, "use" it there through
 *  every registered stripes item handler (a fake BuildCraft player standing on the target, facing north,
 *  exactly as 7.1.x posed it), keeping what is left of the stack in hand after a handled use
 *  ({@link #takeBackFrom}). Ported from 7.1.x
 *  {@code AIRobotStripesHandler}: 7.1.x's manual iteration over {@code PipeManager.stripesHandlers} is
 *  the modern single {@link PipeApi#stripeRegistry}{@code .handleItem} call, which walks the same
 *  handler set the stripes pipe uses. Both {@link IStripesActivator} outputs drop the stack at the
 *  robot's feet, as 7.1.x's {@code InvUtils.dropItems} did — a robot has no pipe to send through.
 *
 *  <p>Reports {@link #success()} {@code false} when no handler took the item at this cell (7.1.x reported
 *  nothing), which {@code BoardRobotStripes} counts to stop retrying an item nothing can use; a use a protection mod
 *  refused is reported apart ({@link #refused()}), as it says nothing about the item. */
public class AIRobotStripesHandler extends AIRobot implements IStripesActivator {
    private BlockPos useToBlock;
    private int useCycles = 0;
    /** Whether a protection mod refused the use; see {@link #refused()}. */
    private boolean refused = false;

    public AIRobotStripesHandler(IRobotAccess iRobot) {
        super(iRobot);
    }

    public AIRobotStripesHandler(IRobotAccess iRobot, BlockPos index) {
        this(iRobot);

        useToBlock = index;
    }

    @Override
    public void start() {
        robot.aimItemAt(useToBlock);
        robot.setItemActive(true);
    }

    @Override
    public void update() {
        if (useToBlock == null) {
            setSuccess(false);
            terminate();
            return;
        }

        useCycles++;

        if (useCycles > 60) {
            ItemStack stack = robot.getHeldItem();

            Direction direction = Direction.NORTH;

            // Leased: the rotation set here never leaks into the next user of the shared player (a stripes
            // pipe placing blocks would otherwise inherit yRot 180), and closing the lease drops whatever the
            // handler put in its hands.
            // As the robot's deployer (see RobotProtection; like the stripes pipe's owner), so a block placed in a
            // claim is asked about as that player, and an advancement a used item grants reaches them. Those events
            // say nothing for the home station's owner, who is asked first about every cell a handler can act on
            // (stationOwnerPermits).
            ServerLevel serverLevel = (ServerLevel) robot.level();
            // The handlers act on pos.relative(direction) (the stripes pipe's convention: pos is the pipe), so
            // they are handed the cell just behind the reserved one. 7.1.x's handlers took the target itself.
            BlockPos activatorPos = useToBlock.relative(direction.getOpposite());
            if (!stationOwnerPermits(serverLevel, useToBlock, activatorPos)) {
                markRefused();
                terminate();
                return;
            }
            try (FakePlayerUtil.Lease lease = RobotProtection.lease(robot, serverLevel)) {
                FakePlayer player = lease.player();
                long refusalsBefore = MachineRefusals.count(player);
                FakePlayerUtil.pose(player, Vec3.atBottomCenterOf(useToBlock), 180, 0);
                // In the player's hand, as the stripes pipe does it: handlers that act through the player
                // (shearing or milking a mob, using an item) read the hand, not their stack argument.
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                ItemStack original = stack.copy();

                if (PipeApi.stripeRegistry.handleItem(robot.level(), activatorPos, direction, stack, player, this)) {
                    takeBackFrom(player, stack, original);
                } else if (refused || MachineRefusals.count(player) != refusalsBefore) {
                    // A protection mod refused the deployer's click, break or mob interaction (the tallies the
                    // stripes dispatch already keeps to end a refused use), or the station's owner was refused where
                    // a use in the air would land (mayUseInAir): the item was not tried, so this is no evidence it is
                    // unusable.
                    markRefused();
                } else {
                    // Nothing took the item here. 7.1.x ended the cycle silently, so its board could not tell this
                    // from a use and retried the same item on the next cell for ever; the failure is what lets the
                    // board count them and give an unusable item up.
                    setSuccess(false);
                }
            }
            terminate();
        }
    }

    /**
     * The home station's owner's say on a use aimed at {@code target}, dispatched from {@code activator} (the cell
     * behind it): true when the owner is the player the robot acts as (that player's own events judge the use), else
     * the closest probe for every cell a handler can touch, read off the handlers' geometry (they act on
     * {@code activator.relative(direction)}, which is {@code target}):
     * <ul>
     * <li>Something may be set down in {@code target} (the dispenser's block or fire, a planted crop, a spawned
     * entity), so it gets the placement probe, the question the Builder asks for the same act. That probe is also the
     * owner's answer for a handler that acts on a mob in the cell (an interaction, shears): there is no entity probe
     * that does not perform the interaction, posting a real one as the owner would run every other mod's interaction
     * handler, and claim mods guard a mob by where it stands.</li>
     * <li>The dispenser's second click is on {@code activator}'s face, and a block item clicked on a replaceable block
     * replaces it (as the first click puts one in {@code activator} when {@code target} is not replaceable), so a
     * replaceable {@code activator} gets the placement probe too.</li>
     * <li>A planting on a filled {@code target} (its second try, with {@code target} as the soil) puts the crop above
     * it, which gets the placement probe.</li>
     * <li>Every one of {@code target}, the floor under it and {@code activator} that is not empty gets the break
     * probe: shears take {@code target} away, a hoe tills {@code target} or its floor, and the dispenser's clicks till,
     * strip or fertilise {@code target} or {@code activator}. A till is asked the break question on the tool-use path
     * too ({@code AIRobotUseToolOnBlock}).</li>
     * </ul>
     * The dispenser's last resort, a use in the air, lands wherever the item's own aim from the posed player meets a
     * block; {@link #mayUseInAir} asks the owner about those cells when the use gets that far. The probes run once per
     * use, not per searched block.
     */
    private boolean stationOwnerPermits(ServerLevel level, BlockPos target, BlockPos activator) {
        if (!RobotProtection.stationOwnerAsksSeparately(robot)) {
            return true;
        }
        GameProfile owner = RobotProtection.stationIdentityOf(robot);
        List<BlockPos> placed = new ArrayList<>(3);
        placed.add(target);
        if (level.getBlockState(activator).canBeReplaced()) {
            placed.add(activator);
        }
        if (!level.isEmptyBlock(target)) {
            placed.add(target.above());
        }
        for (BlockPos cell : placed) {
            if (!BlockUtil.canMachinePlace(level, cell, owner)) {
                return false;
            }
        }
        for (BlockPos cell : List.of(target, target.below(), activator)) {
            if (!level.isEmptyBlock(cell) && !BlockUtil.canMachineBreak(level, cell, owner)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The home station's owner's say on the dispenser fallback's use in the air. The vanilla items that touch the world
     * from there (buckets, bottles, boats, lily pads, spawn eggs, eyes of ender) all aim along the player's eye ray
     * ({@link Item#getPlayerPOVHitResult}), and this robot's player is posed before the dispatch, so the block each
     * fluid mode's ray meets is known before the use. That block and the cell in front of the face it was met on get
     * the placement probe (a bucket pours into either, a boat or an egg is put down in front); a fluid met gets the
     * break probe (a bucket takes it) and the cell above it the placement probe (a lily pad goes on top). A ray that
     * meets nothing within reach touches no block, and a thrown item is the deployer's own projectile, judged by its
     * impact events. True at once when the owner is the player the robot acts as.
     */
    @Override
    public boolean mayUseInAir(Player player, ItemStack stack) {
        if (!RobotProtection.stationOwnerAsksSeparately(robot) || !(player.level() instanceof ServerLevel level)) {
            return true;
        }
        Set<BlockPos> placed = new LinkedHashSet<>();
        Set<BlockPos> taken = new LinkedHashSet<>();
        for (ClipContext.Fluid fluid : ClipContext.Fluid.values()) {
            BlockHitResult hit = Item.getPlayerPOVHitResult(level, player, fluid);
            if (hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            BlockPos pos = hit.getBlockPos();
            placed.add(pos);
            placed.add(pos.relative(hit.getDirection()));
            if (!level.getFluidState(pos).isEmpty()) {
                taken.add(pos);
                placed.add(pos.above());
            }
        }
        GameProfile owner = RobotProtection.stationIdentityOf(robot);
        for (BlockPos cell : placed) {
            if (!BlockUtil.canMachinePlace(level, cell, owner)) {
                markRefused();
                return false;
            }
        }
        for (BlockPos cell : taken) {
            if (!BlockUtil.canMachineBreak(level, cell, owner)) {
                markRefused();
                return false;
            }
        }
        return true;
    }

    /**
     * After a handled use: what is left of the stack (a handler places or uses ONE item and shrinks the rest in
     * place) stays in the robot's hand for the next cell, and anything else the handler left on the player — a
     * filled bucket, a sheared-off drop — is dropped at the robot's feet, where 7.1.x's handlers dropped their
     * results. Before this the hand was simply cleared and the remainder of the stack deleted; 7.1.x itself dropped
     * the remainder on the ground. Which stack is the remainder is {@link FakePlayerUtil#takeLeftovers}'s rule, the
     * one the stripes pipe's sweep follows too. {@code original} is the pre-use copy, which still names the item
     * after {@code used} was consumed.
     */
    private void takeBackFrom(FakePlayer player, ItemStack used, ItemStack original) {
        FakePlayerUtil.Leftovers left = FakePlayerUtil.takeLeftovers(player, used, original.getItem());
        for (ItemStack product : left.products()) {
            sendItem(product, Direction.NORTH);
        }
        robot.setItemInUse(left.remainder());
    }

    /** Whether the use failed because a protection mod refused it, for the robot's deployer or its home station's
     *  owner, rather than because no handler could use the item there: a refusal says nothing about the item, so the
     *  board does not count it towards giving the item up. Always also a failed {@link #success()}. */
    public boolean refused() {
        return refused;
    }

    /** Ends this use as refused by protection; see {@link #refused()}. */
    protected void markRefused() {
        refused = true;
        setSuccess(false);
    }

    @Override
    public void end() {
        robot.setItemActive(false);
    }

    @Override
    public long getPowerCost() {
        // 7.1.x charged 15 RF per handler cycle.
        return 1_500_000;
    }

    @Override
    public boolean sendItem(ItemStack stack, Direction from) {
        ItemEntity entity = new ItemEntity(
                robot.level(),
                robot.position().x,
                robot.position().y,
                robot.position().z,
                stack);
        return robot.level().addFreshEntity(entity);
    }

    @Override
    public void dropItem(ItemStack stack, Direction from) {
        sendItem(stack, from);
    }
}
