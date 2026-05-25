package buildcraft.api.transport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;

import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;

/** Designates an item that can be placed onto a pipe as a {@link PipePluggable}. */
public interface IItemPluggable {
    /** Called when this item is placed onto a pipe holder. This can return null if this item does not make a valid
     * pluggable. Note that if you return a non-null pluggable then it will *definitely* be added to the pipe, and you
     * are responsible for making all the effects yourself (like the sound effect).
     *
     * @param stack The stack that holds this item
     * @param holder The pipe holder
     * @param side The side that the pluggable should be placed on
     * @return A pluggable to place onto the pipe */
    @Nullable
    PipePluggable onPlace(@Nonnull ItemStack stack, IPipeHolder holder, Direction side, Player player,
        InteractionHand hand);

    /** Bounding box this item would occupy as a pluggable on {@code side} of a pipe — drives the
     * placement-preview outline so it traces exactly what {@link #onPlace} will produce, instead
     * of always showing a gate-sized panel that larger pluggables (facades, power adaptors, lenses)
     * would visually swallow.
     *
     * <p>Coordinates are in pipe-local block space (0..1 per axis). Default returns a gate-sized
     * 6×6 panel — fits most pluggables; override when {@link #onPlace} creates a pluggable with a
     * non-gate-sized {@link PipePluggable#getBoundingBox()}.
     *
     * <p>Called every frame the outline is rendered, so implementations must be side-effect-free
     * and should return cached static instances rather than allocating. */
    @Nonnull
    default AABB getPlacementBoundingBox(@Nonnull ItemStack stack, Direction side) {
        return DefaultPlacementBoxes.BOXES[side.get3DDataValue()];
    }

    /** Holder for the gate-sized default placement box, materialised once per JVM. Nested so the
     *  interface stays a pure contract — no static-init order issues for implementers. */
    final class DefaultPlacementBoxes {
        private DefaultPlacementBoxes() {}

        /** 6×6 panel 2 px deep, just outside the 4–12 pipe core — matches the layout used by
         *  {@code PluggableGate}, the most common pluggable shape. */
        static final AABB[] BOXES = new AABB[6];
        static {
            double a = 5 / 16.0, b = 11 / 16.0;
            double near = 2 / 16.0, nearEnd = 4 / 16.0, far = 12 / 16.0, farEnd = 14 / 16.0;
            BOXES[Direction.DOWN.get3DDataValue()]  = new AABB(a, near, a, b, nearEnd, b);
            BOXES[Direction.UP.get3DDataValue()]    = new AABB(a, far, a, b, farEnd, b);
            BOXES[Direction.NORTH.get3DDataValue()] = new AABB(a, a, near, b, b, nearEnd);
            BOXES[Direction.SOUTH.get3DDataValue()] = new AABB(a, a, far, b, b, farEnd);
            BOXES[Direction.WEST.get3DDataValue()]  = new AABB(near, a, a, nearEnd, b, b);
            BOXES[Direction.EAST.get3DDataValue()]  = new AABB(far, a, a, farEnd, b, b);
        }
    }
}
