package buildcraft.api.core;

import buildcraft.api.fluids.BCFluidStack;

/** A fluid handler that can extract a fluid which a fluid filter accepts.
 *
 * <p>A BuildCraft-owned shape in the style of the old BuildCraft API (the 1.12 {@code IFluidHandler} it used to
 * extend): plain methods with a {@code simulate} flag over {@link BCFluidStack}s, no loader handler and no transaction
 * types, so the API names no loader. BuildCraft adapts it to and from its transactional fluid storages
 * ({@code buildcraft.lib.transfer.FluidHandlerAdvs}): each call there is a transaction of its own, nested in the one
 * open on the thread if any, committed unless the call simulates. Amounts are millibuckets. */
public interface IFluidHandlerAdv {
    /** @return The number of tanks. */
    int getTanks();

    /** @return A copy of the fluid in {@code tank}, or {@link BCFluidStack#EMPTY}. */
    BCFluidStack getFluidInTank(int tank);

    /** @return How many millibuckets {@code tank} holds at most. */
    int getTankCapacity(int tank);

    /** Fills the handler with up to {@code resource}'s amount of its fluid.
     *
     * @return The amount that went in (or would have, when simulating). */
    int fill(BCFluidStack resource, boolean simulate);

    /** Drains up to {@code resource}'s amount of its fluid.
     *
     * @return What came out (or would have), or {@link BCFluidStack#EMPTY}. */
    BCFluidStack drain(BCFluidStack resource, boolean simulate);

    /** Drains up to {@code maxDrain} of whatever fluid the first non-empty tank holds.
     *
     * @return What came out (or would have), or {@link BCFluidStack#EMPTY}. */
    BCFluidStack drain(int maxDrain, boolean simulate);

    /** Extracts fluid out of internal tanks, distribution is left entirely to the handler.
     *
     * @param filter A filter to filter the possible fluids that can be extracted.
     * @param maxDrain The maximum amount of fluid to extract
     * @return What came out (or would have), or {@link BCFluidStack#EMPTY}. */
    BCFluidStack drain(IFluidFilter filter, int maxDrain, boolean simulate);
}
