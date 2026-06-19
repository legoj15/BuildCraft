package buildcraft.api.mj;

import java.text.DecimalFormat;

import javax.annotation.Nonnull;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.capabilities.BlockCapability;

import org.jspecify.annotations.Nullable;

public class MjAPI {

    // ################################
    //
    // Useful constants (Public API)
    //
    // ################################

    /** A single minecraft joule, in micro joules (the power system base unit) */
    public static final long ONE_MINECRAFT_JOULE = getMjValue();
    /** The same as {@link #ONE_MINECRAFT_JOULE}, but a shorter field name */
    public static final long MJ = ONE_MINECRAFT_JOULE;

    /** The decimal format used to display values of MJ to the player. Note that this */
    public static final DecimalFormat MJ_DISPLAY_FORMAT = new DecimalFormat("#,##0.##");

    public static IMjEffectManager EFFECT_MANAGER = NullaryEffectManager.INSTANCE;

    /** MJ ⇄ RF conversion config, read at runtime. Populated by the live mod from its config at init (see
     * {@code BCUnifiedConfig}); third parties compiling against the API see the defaults below — which match
     * BuildCraft's own {@code MJ_ONLY} / 0.1-ratio defaults — until BuildCraft installs the real backing. */
    public interface IMjConfig {
        /** MJ-per-RF conversion ratio (only consulted when {@link #isRfAutoConvertEnabled()} is true). */
        double getRfConversionAmount();

        /** Whether MJ ⇄ RF auto-conversion is enabled (the {@code powerMode}'s {@code autoconvert} flag). */
        boolean isRfAutoConvertEnabled();
    }

    public static IMjConfig config = new IMjConfig() {
        @Override
        public double getRfConversionAmount() {
            return 0.1;
        }

        @Override
        public boolean isRfAutoConvertEnabled() {
            return false;
        }
    };

    // ###############
    //
    // Helpful methods
    //
    // ###############

    /** Formats a given MJ value to a player-oriented string. Note that this does not append "MJ" to the value. */
    public static String formatMj(long microMj) {
        return formatMjInternal(microMj / (double) MJ);
    }

    private static String formatMjInternal(double val) {
        return MJ_DISPLAY_FORMAT.format(val);
    }

    public static MjRfConversion getRfConversion() {
        return MjRfConversion.createParsed(config.getRfConversionAmount());
    }

    public static boolean isRfAutoConversionEnabled() {
        return config.isRfAutoConvertEnabled();
    }

    // ########################################
    //
    // Null based classes
    //
    // ########################################

    public enum NullaryEffectManager implements IMjEffectManager {
        INSTANCE;
        @Override
        public void createPowerLossEffect(Level world, Vec3 center, long microJoulesLost) {}

        @Override
        public void createPowerLossEffect(Level world, Vec3 center, Direction direction, long microJoulesLost) {}

        @Override
        public void createPowerLossEffect(Level world, Vec3 center, Vec3 direction, long microJoulesLost) {}
    }
    // @formatter:on

    // ###############
    //
    // Capabilities (NeoForge BlockCapability)
    //
    // ###############

    /** MJ connector capability — used for visual connection checks. */
    @Nonnull
    public static final BlockCapability<IMjConnector, @Nullable Direction> CAP_CONNECTOR =
        BlockCapability.createSided(Identifier.fromNamespaceAndPath("buildcraftunofficial", "mj_connector"), IMjConnector.class);

    /** MJ receiver capability — used by engines to find power consumers. */
    @Nonnull
    public static final BlockCapability<IMjReceiver, @Nullable Direction> CAP_RECEIVER =
        BlockCapability.createSided(Identifier.fromNamespaceAndPath("buildcraftunofficial", "mj_receiver"), IMjReceiver.class);

    /** MJ redstone receiver capability — used by stripes pipe and other similar blocks. */
    @Nonnull
    public static final BlockCapability<IMjRedstoneReceiver, @Nullable Direction> CAP_REDSTONE_RECEIVER =
        BlockCapability.createSided(Identifier.fromNamespaceAndPath("buildcraftunofficial", "mj_redstone_receiver"), IMjRedstoneReceiver.class);

    /** MJ readable capability — exposes stored/capacity power (used by the Power gate trigger). */
    @Nonnull
    public static final BlockCapability<IMjReadable, @Nullable Direction> CAP_READABLE =
        BlockCapability.createSided(Identifier.fromNamespaceAndPath("buildcraftunofficial", "mj_readable"), IMjReadable.class);

    /** MJ passive provider capability — power sources that powered wooden kinesis pipes pull from. */
    @Nonnull
    public static final BlockCapability<IMjPassiveProvider, @Nullable Direction> CAP_PASSIVE_PROVIDER =
        BlockCapability.createSided(Identifier.fromNamespaceAndPath("buildcraftunofficial", "mj_passive_provider"), IMjPassiveProvider.class);

    private static long getMjValue() {
        return 1_000_000L;
    }
}
