package buildcraft.transport.pipe.behaviour;

import java.util.List;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.IFlowItems;
import buildcraft.api.transport.pipe.IPipe;
import buildcraft.api.transport.pipe.PipeBehaviour;
import buildcraft.api.transport.pipe.PipeEventHandler;
import buildcraft.api.transport.pipe.PipeEventItem;
import buildcraft.api.transport.pipe.PipeFlow;

import buildcraft.lib.misc.BoundingBoxUtil;
import buildcraft.lib.misc.VecUtil;

/**
 * Obsidian pipe — sucks up dropped items.
 * <p>
 * Two pickup modes:
 * <ol>
 *   <li>Free pickup when an entity walks/falls onto the pipe (via onEntityCollide)</li>
 *   <li>MJ-powered vacuuming at 1–4 block range (via receivePower from engines)</li>
 * </ol>
 * Faithfully ported from the 1.12.2 implementation.
 */
public class PipeBehaviourObsidian extends PipeBehaviour implements IMjRedstoneReceiver {
    private static final long POWER_PER_ITEM = MjAPI.MJ / 2;
    private static final long POWER_PER_METRE = MjAPI.MJ / 4;

    private static final double INSERT_SPEED = 0.04;
    private static final int DROP_GAP = 20;

    /** Map of recently dropped items to the tick when they can be picked up again. */
    private final WeakHashMap<ItemEntity, Long> entityDropTime = new WeakHashMap<>();
    private int toWaitTicks = 0;

    public PipeBehaviourObsidian(IPipe pipe) {
        super(pipe);
    }

    public PipeBehaviourObsidian(IPipe pipe, CompoundTag nbt) {
        super(pipe, nbt);
        // On load, reset the drop gap rather than serialising entity references
        toWaitTicks = DROP_GAP;
    }

    @Override
    public CompoundTag writeToNbt() {
        return super.writeToNbt();
    }

    @Override
    public void onTick() {
        if (pipe.getHolder().getPipeWorld().isClientSide()) {
            return;
        }
        toWaitTicks--;
        if (toWaitTicks > 0) {
            return;
        } else {
            toWaitTicks = 0;
        }

        // Free pickup: scan for entities directly on the pipe (replaces entityInside
        // which is not available as a block override in this NeoForge version)
        Direction openFace = getOpenFace();
        if (openFace != null) {
            AABB collisionBox = new AABB(pipe.getHolder().getPipePos());
            List<Entity> entities = pipe.getHolder().getPipeWorld()
                .getEntities((Entity) null, collisionBox, e -> e instanceof ItemEntity && e.isAlive());
            for (Entity entity : entities) {
                trySuckEntity(entity, openFace, Long.MAX_VALUE, false);
            }
        }
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof PipeBehaviourObsidian);
    }

    /**
     * Finds the single unconnected face — this is the direction the pipe sucks from.
     * Returns null if no single face is found (e.g. pipe is fully surrounded or has multiple open faces).
     */
    private Direction getOpenFace() {
        Direction openFace = null;
        for (Direction face : Direction.values()) {
            if (pipe.isConnected(face)) {
                if (openFace == null) {
                    openFace = face.getOpposite();
                } else {
                    return null;
                }
            }
        }
        return openFace;
    }

    /**
     * Creates an AABB offset from the pipe in the given direction and inflated to cover a distance tier.
     */
    protected AABB getSuckingBox(Direction openFace, int distance) {
        AABB bb = BoundingBoxUtil.makeAround(VecUtil.convertCenter(pipe.getHolder().getPipePos()), 0.4);
        return switch (openFace) {
            case WEST  -> bb.move(-distance, 0, 0).inflate(0.5, distance, distance);
            case EAST  -> bb.move(distance, 0, 0).inflate(0.5, distance, distance);
            case DOWN  -> bb.move(0, -distance, 0).inflate(distance, 0.5, distance);
            case UP    -> bb.move(0, distance, 0).inflate(distance, 0.5, distance);
            case NORTH -> bb.move(0, 0, -distance).inflate(distance, distance, 0.5);
            case SOUTH -> bb.move(0, 0, distance).inflate(distance, distance, 0.5);
        };
    }

    /**
     * Attempts to suck items from the given entity into the pipe flow.
     *
     * @return The leftover power that was NOT consumed.
     */
    protected long trySuckEntity(Entity entity, Direction faceFrom, long power, boolean simulate) {
        if (!entity.isAlive() || entity instanceof LivingEntity) {
            return power;
        }

        // Check the drop delay — don't re-suck items we just ejected
        if (entity instanceof ItemEntity itemEntity) {
            Long tickPickupObj = entityDropTime.get(itemEntity);
            if (tickPickupObj != null) {
                long tickNow = pipe.getHolder().getPipeWorld().getGameTime();
                if (tickNow < tickPickupObj) {
                    return power;
                } else {
                    entityDropTime.remove(itemEntity);
                }
            }
        }

        PipeFlow flow = pipe.getFlow();
        if (!(flow instanceof IFlowItems flowItems)) {
            return power;
        }

        // Only handle ItemEntity for now (fluid support is TODO, same as 1.12.2)
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                return power;
            }

            long powerReqPerItem;
            int max;
            if (power == Long.MAX_VALUE) {
                // Free pickup (entity collision) — no power cost
                max = Integer.MAX_VALUE;
                powerReqPerItem = 0;
            } else {
                double distance = Math.sqrt(entity.distanceToSqr(
                    Vec3.atCenterOf(pipe.getHolder().getPipePos())));
                powerReqPerItem = (long) (Math.max(1, distance) * POWER_PER_METRE + POWER_PER_ITEM);
                max = (int) (power / powerReqPerItem);
            }

            if (max <= 0) {
                return power;
            }

            // Extract up to 'max' items from the stack
            int toExtract = Math.min(stack.getCount(), max);
            ItemStack extracted = stack.copyWithCount(toExtract);

            if (!simulate) {
                // Shrink the entity's stack, or discard the entity if everything was taken
                if (toExtract >= stack.getCount()) {
                    itemEntity.discard();
                } else {
                    stack.shrink(toExtract);
                    itemEntity.setItem(stack);
                }
                flowItems.insertItemsForce(extracted, faceFrom, null, INSERT_SPEED);
            }
            return power - powerReqPerItem * toExtract;
        }
        return power;
    }

    /**
     * Tracks dropped items so we don't immediately re-suck them.
     */
    @PipeEventHandler
    public void onPipeDrop(PipeEventItem.Drop drop) {
        entityDropTime.put(drop.getEntity(),
            pipe.getHolder().getPipeWorld().getGameTime() + DROP_GAP);
    }

    // ==================== IMjRedstoneReceiver ====================

    @Override
    public boolean canConnect(@Nonnull IMjConnector other) {
        return true;
    }

    @Override
    public long getPowerRequested() {
        final long power = 512 * MjAPI.MJ;
        return power - receivePower(power, true);
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        if (toWaitTicks > 0) {
            return microJoules;
        }
        Direction openFace = getOpenFace();
        if (openFace == null) {
            return microJoules;
        }

        // Scan expanding distance tiers (1–4 blocks)
        for (int d = 1; d < 5; d++) {
            AABB aabb = getSuckingBox(openFace, d);
            List<Entity> discoveredEntities = pipe.getHolder().getPipeWorld()
                .getEntities((Entity) null, aabb, e -> true);

            for (Entity entity : discoveredEntities) {
                long leftOver = trySuckEntity(entity, openFace, microJoules, simulate);
                if (leftOver < microJoules) {
                    return leftOver;
                }
            }
        }
        // Still consume a base amount of power even if nothing was found
        return microJoules - MjAPI.MJ;
    }

    // ==================== Capability ====================

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(@Nonnull Object capability, Direction facing) {
        if (capability == MjAPI.CAP_RECEIVER
            || capability == MjAPI.CAP_CONNECTOR
            || capability == MjAPI.CAP_REDSTONE_RECEIVER) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }
}
