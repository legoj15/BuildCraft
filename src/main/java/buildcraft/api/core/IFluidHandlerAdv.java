package buildcraft.api.core;

import javax.annotation.Nullable;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?}

/** A fluid handler that can extract a fluid which a fluid filter accepts.
 * <p>On 1.21.10+ this is a NeoForge {@code ResourceHandler<FluidResource>} (the Transfer API); on 1.21.1 the
 * Transfer API does not exist, so it is a classic {@code IFluidHandler} and the transaction context collapses to
 * a {@code boolean simulate}. The {@code >=1.21.10} branch is exactly today's declaration, so the released nodes
 * are unchanged. */
//? if >=1.21.10 {
public interface IFluidHandlerAdv extends ResourceHandler<FluidResource> {
    /** Extracts fluid out of internal tanks, distribution is left entirely to the ResourceHandler.
     *
     * @param filter A filter to filter the possible fluids that can be extracted.
     * @param maxDrain The maximum amount of fluid to extract
     * @param tx The transaction context for simulated or executed behavior.
     * @return The amount of fluid that was extracted. */
    int extract(IFluidFilter filter, int maxDrain, TransactionContext tx);
}
//?} else {
/*public interface IFluidHandlerAdv extends net.neoforged.neoforge.fluids.capability.IFluidHandler {
    int extract(IFluidFilter filter, int maxDrain, boolean simulate);
}*/
//?}
