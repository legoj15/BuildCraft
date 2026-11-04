package buildcraft.lib.mj;

import buildcraft.api.mj.IMjConnector;
import buildcraft.api.mj.IMjReadable;
import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.IMjRedstoneReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.BCTransaction;

/**
 * Automatic conversion utility: wraps a Forge energy handler (FE/RF) and presents it as an MJ
 * receiver/provider. Used by BC engines to send MJ power to non-MJ machines that only expose FE/RF.
 *
 * <p>The wrapped handler is the platform layer's {@link BCEnergyStorage} view of the neighbour's energy handler
 * (moved out of the API in the transfer slice: the API may not name the platform's types).
 */
public class MjToRfAutoConvertor implements IMjReadable {

    final BCEnergyStorage fe;

    /** @return An {@link MjToRfAutoConvertor} that implements {@link IMjReceiver} if the given handler can receive
     *         energy, or null if the given handler is null, or if RF&lt;-&gt;MJ autoconversion is not enabled
     *         ( {@link MjAPI#isRfAutoConversionEnabled()} ) */
    public static MjToRfAutoConvertor create(BCEnergyStorage fe) {
        if (fe == null) {
            return null;
        }
        if (!MjAPI.isRfAutoConversionEnabled()) {
            return null;
        }
        // Forge energy handlers don't expose canReceive/canExtract flags here,
        // so we return the full OfBoth variant which handles both directions.
        return new OfBoth(fe);
    }

    /** @return An {@link MjToRfAutoConvertor} that implements {@link IMjReceiver}
     *         if autoconversion is enabled, or null otherwise. */
    public static IMjReceiver createReceiver(BCEnergyStorage fe) {
        MjToRfAutoConvertor convertor = create(fe);
        if (convertor instanceof IMjReceiver) {
            return (IMjReceiver) convertor;
        } else {
            return null;
        }
    }

    MjToRfAutoConvertor(BCEnergyStorage handler) {
        this.fe = handler;
    }

    // --- Version-neutral accessors over the wrapped Forge energy handler ---

    private long feStored() {
        return fe.getAmountAsLong();
    }

    private long feCapacity() {
        return fe.getCapacityAsLong();
    }

    /** Insert up to {@code rf} into the handler; returns the amount actually inserted. */
    private int feInsert(int rf, boolean simulate) {
        try (BCTransaction tx = BCTransaction.openRoot()) {
            int received = fe.insert(rf, tx);
            if (!simulate) {
                tx.commit();
            }
            return received;
        }
    }

    /** @return true. (Redstone-like engines are expected to not connect due to this class never implementing
     *         {@link IMjRedstoneReceiver}) */
    @Override
    public boolean canConnect(IMjConnector other) {
        return true;
    }

    @Override
    public long getStored() {
        return feStored() * MjAPI.getRfConversion().mjPerRf;
    }

    @Override
    public long getCapacity() {
        return feCapacity() * MjAPI.getRfConversion().mjPerRf;
    }

    long implGetPowerRequested() {
        return (feCapacity() - feStored()) * MjAPI.getRfConversion().mjPerRf;
    }

    /** @return excess MJ not accepted */
    long implReceivePower(long microJoules, boolean simulate) {
        long mjPerRf = MjAPI.getRfConversion().mjPerRf;
        int maxRf = (int) (microJoules / mjPerRf);
        if (maxRf <= 0) {
            return microJoules;
        }
        int received = feInsert(maxRf, simulate);
        return microJoules - received * mjPerRf;
    }
}

final class OfBoth extends MjToRfAutoConvertor implements IMjReceiver {

    OfBoth(BCEnergyStorage handler) {
        super(handler);
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @Override
    public long getPowerRequested() {
        return implGetPowerRequested();
    }

    @Override
    public long receivePower(long microJoules, boolean simulate) {
        return implReceivePower(microJoules, simulate);
    }
}
