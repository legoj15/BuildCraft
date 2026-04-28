package buildcraft.lib.client.guide.entry;

import java.util.Objects;

import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

/** Mirror of {@link ItemStackValueFilter} for fluids — used as the value type of
 *  {@link PageEntryFluidStack} so guide groups can hold fluid endpoints (fuels, coolants,
 *  refinery in/out fluids) alongside item endpoints in the same {@link
 *  buildcraft.lib.client.guide.ref.GuideGroupSet}.
 *
 *  <p>Equality compares the fluid identity only (NOT amount or NBT) — two filters wrapping
 *  any amount of the same fluid are equal. Most guide-book uses don't care about amount
 *  (we want "diesel" to match "diesel" regardless of bucket size). */
public class FluidStackValueFilter {
    public final FluidStack stack;

    public FluidStackValueFilter(FluidStack stack) {
        this.stack = stack;
    }

    public FluidStackValueFilter(Fluid fluid) {
        this(new FluidStack(fluid, 1));
    }

    public Fluid getFluid() {
        return stack.getFluid();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        FluidStackValueFilter other = (FluidStackValueFilter) obj;
        return stack.getFluid() == other.stack.getFluid();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(stack.getFluid());
    }

    @Override
    public String toString() {
        return "FluidStackValueFilter[" + stack.getFluid() + "]";
    }
}
