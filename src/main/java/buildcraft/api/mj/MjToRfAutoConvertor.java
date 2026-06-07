package buildcraft.api.mj;

//? if >=1.21.10 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
//?} else {
/*import net.neoforged.neoforge.energy.IEnergyStorage;*/
//?}

/**
 * Automatic conversion utility: wraps a Forge energy handler (FE/RF) and presents it as an MJ
 * receiver/provider. Used by BC engines to send MJ power to non-MJ machines that only expose FE/RF.
 *
 * <p>On 1.21.10+ the wrapped handler is a NeoForge transactional {@code EnergyHandler}; on 1.21.1
 * the Transfer API does not exist, so it is the classic {@code IEnergyStorage} and the transaction
 * collapses to a {@code boolean simulate}. All four {@code fe*} accessors below hide that split so the
 * MJ-facing logic is shared; the {@code >=1.21.10} code is exactly today's.
 */
public class MjToRfAutoConvertor implements IMjReadable {

    //? if >=1.21.10 {
    final EnergyHandler fe;
    //?} else {
    /*final IEnergyStorage fe;*/
    //?}

    /** @return An {@link MjToRfAutoConvertor} that may implement {@link IMjPassiveProvider} and/or {@link IMjReceiver}
     *         if the given handler can provide/receive energy, or null if the given handler is null, or if
     *         RF&lt;-&gt;MJ autoconversion is not enabled ( {@link MjAPI#isRfAutoConversionEnabled()} ) */
    //? if >=1.21.10 {
    public static MjToRfAutoConvertor create(EnergyHandler fe) {
    //?} else {
    /*public static MjToRfAutoConvertor create(IEnergyStorage fe) {*/
    //?}
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
    //? if >=1.21.10 {
    public static IMjReceiver createReceiver(EnergyHandler fe) {
    //?} else {
    /*public static IMjReceiver createReceiver(IEnergyStorage fe) {*/
    //?}
        MjToRfAutoConvertor convertor = create(fe);
        if (convertor instanceof IMjReceiver) {
            return (IMjReceiver) convertor;
        } else {
            return null;
        }
    }

    /** @return An {@link MjToRfAutoConvertor} that implements {@link IMjPassiveProvider}
     *         if autoconversion is enabled, or null otherwise. */
    //? if >=1.21.10 {
    public static IMjPassiveProvider createProvider(EnergyHandler fe) {
    //?} else {
    /*public static IMjPassiveProvider createProvider(IEnergyStorage fe) {*/
    //?}
        MjToRfAutoConvertor convertor = create(fe);
        if (convertor instanceof IMjPassiveProvider) {
            return (IMjPassiveProvider) convertor;
        } else {
            return null;
        }
    }

    //? if >=1.21.10 {
    MjToRfAutoConvertor(EnergyHandler handler) {
    //?} else {
    /*MjToRfAutoConvertor(IEnergyStorage handler) {*/
    //?}
        this.fe = handler;
    }

    // --- Version-neutral accessors over the wrapped Forge energy handler ---

    private long feStored() {
        //? if >=1.21.10 {
        return fe.getAmountAsLong();
        //?} else {
        /*return fe.getEnergyStored();*/
        //?}
    }

    private long feCapacity() {
        //? if >=1.21.10 {
        return fe.getCapacityAsLong();
        //?} else {
        /*return fe.getMaxEnergyStored();*/
        //?}
    }

    /** Insert up to {@code rf} into the handler; returns the amount actually inserted. */
    private int feInsert(int rf, boolean simulate) {
        //? if >=1.21.10 {
        try (Transaction tx = Transaction.openRoot()) {
            int received = fe.insert(rf, tx);
            if (!simulate) {
                tx.commit();
            }
            return received;
        }
        //?} else {
        /*return fe.receiveEnergy(rf, simulate);*/
        //?}
    }

    /** Extract up to {@code rf} from the handler; returns the amount actually extracted. */
    private int feExtract(int rf, boolean simulate) {
        //? if >=1.21.10 {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = fe.extract(rf, tx);
            if (!simulate) {
                tx.commit();
            }
            return extracted;
        }
        //?} else {
        /*return fe.extractEnergy(rf, simulate);*/
        //?}
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

    long implExtractPower(long min, long max, boolean simulate) {
        long mjPerRf = MjAPI.getRfConversion().mjPerRf;
        int maxRf = (int) (max / mjPerRf);
        if (maxRf <= 0) {
            return 0;
        }

        // Simulate first to check if we meet the minimum.
        long extractedMJ = feExtract(maxRf, true) * mjPerRf;
        if (extractedMJ < min) {
            return 0;
        }

        if (!simulate) {
            return feExtract(maxRf, false) * mjPerRf;
        }
        return extractedMJ;
    }
}

final class OfBoth extends MjToRfAutoConvertor implements IMjReceiver, IMjPassiveProvider {

    //? if >=1.21.10 {
    OfBoth(net.neoforged.neoforge.transfer.energy.EnergyHandler handler) {
    //?} else {
    /*OfBoth(net.neoforged.neoforge.energy.IEnergyStorage handler) {*/
    //?}
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
