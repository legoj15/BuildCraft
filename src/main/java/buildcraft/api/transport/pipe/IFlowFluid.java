package buildcraft.api.transport.pipe;

import javax.annotation.Nullable;


import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import buildcraft.api.core.IFluidFilter;
import buildcraft.api.core.IFluidHandlerAdv;
import buildcraft.api.transport.pluggable.PipePluggable;

public interface IFlowFluid {
    /** @deprecated use the version below with a simulate paramater. */
    @Nullable
    @Deprecated
    default FluidStack tryExtractFluid(int millibuckets, Direction from, FluidStack filter) {
        return tryExtractFluid(millibuckets, from, filter, false);
    }

    /** @param millibuckets
     * @param from
     * @param filter The fluidstack that the extracted fluid must match, or null for any fluid.
     * @return The fluidstack extracted and inserted into the pipe. */
    @Nullable
    FluidStack tryExtractFluid(int millibuckets, Direction from, FluidStack filter, boolean simulate);

    /** @deprecated use the version below with a simulate paramater. */
    @Deprecated
    default Object tryExtractFluidAdv(int millibuckets, Direction from, IFluidFilter filter) {
        return tryExtractFluidAdv(millibuckets, from, filter, false);
    }

    /** Advanced version of {@link #tryExtractFluid(int, Direction, FluidStack, boolean)}. Note that this only works for
     * instances of {@link IFluidHandler} that ALSO extends {@link IFluidHandlerAdv}
     * 
     * @param millibuckets
     * @param from
     * @param filter A filter to try and match fluids.
     * @return The fluidstack extracted and inserted into the pipe. If {@link ActionResult#getType()} equals
     *         {@link InteractionResult#PASS} then it means that the {@link IFluidHandler} didn't implement
     *         {@link IFluidHandlerAdv} and you should call the basic version, if you can. */
    Object tryExtractFluidAdv(int millibuckets, Direction from, IFluidFilter filter, boolean simulate);

    /** Attempts to insert a fluid directly into the pipe. Note that this will fail if the pipe currently contains a
     * different fluid type.
     * 
     * @param from The side that the fluid should *not* go in, or null if the fluid may flow in any direction.
     * @return The amount of fluid that was accepted, or 0 if no fluid was accepted. */
    int insertFluidsForce(FluidStack fluid, @Nullable Direction from, boolean simulate);

    /** Tries to extract fluids directly from the pipe. NOTE: This is intended for {@link PipeBehaviour} and
     * {@link PipePluggable} implementors ONLY! This will result in very buggy behaviour if external tiles try to use
     * this!
     * 
     * @param min The minimum amount of fluid to extract. If less than this amount is in the given center then nothing
     *            will be extracted.
     * @param section The section to extract from. Null means the center.
     * @param simulate
     * @return */
    @Nullable
    FluidStack extractFluidsForce(int min, int max, @Nullable Direction section, boolean simulate);
}

