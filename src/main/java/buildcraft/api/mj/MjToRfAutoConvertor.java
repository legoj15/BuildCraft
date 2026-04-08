package buildcraft.api.mj;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Automatic conversion utility: wraps a NeoForge {@link EnergyHandler} (FE/RF)
 * and presents it as an MJ receiver/provider. Used by BC engines to send MJ power
 * to non-MJ machines that only expose FE/RF.
 *
 * <p>Updated for NeoForge 1.21.11's transactional {@link EnergyHandler} API,
 * replacing the deprecated {@code IEnergyStorage} approach.
 */
public class MjToRfAutoConvertor implements IMjReadable {

    final EnergyHandler fe;

    /** @return An {@link MjToRfAutoConvertor} that may implement {@link IMjPassiveProvider} and/or {@link IMjReceiver}
     *         if the given handler can provide/receive energy, or null if the given handler is null, or if
     *         RF&lt;-&gt;MJ autoconversion is not enabled ( {@link MjAPI#isRfAutoConversionEnabled()} ) */
    public static MjToRfAutoConvertor create(EnergyHandler fe) {
        if (fe == null) {
            return null;
        }
        if (!MjAPI.isRfAutoConversionEnabled()) {
            return null;
        }
        // NeoForge EnergyHandler doesn't have canReceive/canExtract flags,
        // so we return the full OfBoth variant which handles both directions.
        return new OfBoth(fe);
    }

    /** @return An {@link MjToRfAutoConvertor} that implements {@link IMjReceiver}
     *         if autoconversion is enabled, or null otherwise. */
    public static IMjReceiver createReceiver(EnergyHandler fe) {
        MjToRfAutoConvertor convertor = create(fe);
        if (convertor instanceof IMjReceiver) {
            return (IMjReceiver) convertor;
        } else {
            return null;
        }
    }

    /** @return An {@link MjToRfAutoConvertor} that implements {@link IMjPassiveProvider}
     *         if autoconversion is enabled, or null otherwise. */
    public static IMjPassiveProvider createProvider(EnergyHandler fe) {
        MjToRfAutoConvertor convertor = create(fe);
        if (convertor instanceof IMjPassiveProvider) {
            return (IMjPassiveProvider) convertor;
        } else {
            return null;
        }
    }

    MjToRfAutoConvertor(EnergyHandler handler) {
        this.fe = handler;
    }

    /** @return true. (Redstone-like engines are expected to not connect due to this class never implementing
     *         {@link IMjRedstoneReceiver}) */
    @Override
    public boolean canConnect(IMjConnector other) {
        return true;
    }

    @Override
    public long getStored() {
        return fe.getAmountAsLong() * MjAPI.getRfConversion().mjPerRf;
    }

    @Override
    public long getCapacity() {
        return fe.getCapacityAsLong() * MjAPI.getRfConversion().mjPerRf;
    }

    long implGetPowerRequested() {
        return (fe.getCapacityAsLong() - fe.getAmountAsLong()) * MjAPI.getRfConversion().mjPerRf;
    }

    /** @return excess MJ not accepted */
    long implReceivePower(long microJoules, boolean simulate) {
        long mjPerRf = MjAPI.getRfConversion().mjPerRf;
        int maxRf = (int) (microJoules / mjPerRf);
        if (maxRf <= 0) {
            return microJoules;
        }

        if (simulate) {
            // Use a nested transaction that we abort to simulate
            try (Transaction tx = Transaction.openRoot()) {
                int received = fe.insert(maxRf, tx);
                // Abort (don't commit) — this was just a simulation
                return microJoules - received * mjPerRf;
            }
        } else {
            try (Transaction tx = Transaction.openRoot()) {
                int received = fe.insert(maxRf, tx);
                tx.commit();
                return microJoules - received * mjPerRf;
            }
        }
    }

    long implExtractPower(long min, long max, boolean simulate) {
        long mjPerRf = MjAPI.getRfConversion().mjPerRf;
        int maxRf = (int) (max / mjPerRf);
        if (maxRf <= 0) {
            return 0;
        }

        // Simulate first to check if we meet minimum
        long extractedMJ;
        try (Transaction simTx = Transaction.openRoot()) {
            int extractedRF = fe.extract(maxRf, simTx);
            extractedMJ = extractedRF * mjPerRf;
            // Don't commit — this is simulation
        }

        if (extractedMJ < min) {
            return 0;
        }

        if (!simulate) {
            try (Transaction tx = Transaction.openRoot()) {
                int extractedRF = fe.extract(maxRf, tx);
                tx.commit();
                return extractedRF * mjPerRf;
            }
        }

        return extractedMJ;
    }
}

final class OfBoth extends MjToRfAutoConvertor implements IMjReceiver, IMjPassiveProvider {

    OfBoth(EnergyHandler handler) {
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

    @Override
    public long extractPower(long min, long max, boolean simulate) {
        return implExtractPower(min, max, simulate);
    }
}
