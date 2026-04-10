package buildcraft.api.core;

import javax.annotation.Nullable;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** A version of {@link ResourceHandler} that can extract a fluid that a fluid filter accepts. */
public interface IFluidHandlerAdv extends ResourceHandler<FluidResource> {
    /** Extracts fluid out of internal tanks, distribution is left entirely to the ResourceHandler.
     *
     * @param filter A filter to filter the possible fluids that can be extracted.
     * @param maxDrain The maximum amount of fluid to extract
     * @param tx The transaction context for simulated or executed behavior.
     * @return The amount of fluid that was extracted. */
    int extract(IFluidFilter filter, int maxDrain, TransactionContext tx);
}

