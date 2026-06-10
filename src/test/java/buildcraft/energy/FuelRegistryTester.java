package buildcraft.energy;

import java.util.Collection;

import net.minecraft.gametest.framework.GameTestHelper;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.fuels.BuildcraftFuelRegistry;
import buildcraft.api.fuels.ICoolant;
import buildcraft.api.fuels.IFuel;
import buildcraft.api.fuels.IFuelManager.IDirtyFuel;
import buildcraft.api.fuels.ISolidCoolant;
import buildcraft.api.mj.MjAPI;

/**
 * Guards the data source behind the JEI engine-fuel categories
 * ({@code buildcraft.energy.compat.jei}). Those categories and the engine catalyst lookups read
 * {@link BuildcraftFuelRegistry} directly, so if fuel/coolant registration regresses the JEI
 * recipe holders silently empty out. JEI rendering itself is client-only and unit-untestable, but
 * the registry contents are server-side and exactly what this asserts.
 *
 * <p>Runs in the game-test server, where {@code BCEnergyRecipes.ensureInitialized()} populates the registry on
 * {@code ServerAboutToStartEvent} (the same hook the shipped Distiller category depends on).
 */
public class FuelRegistryTester {

    public static void testFuelRegistryPopulatedForJei(GameTestHelper helper) {
        if (BuildcraftFuelRegistry.fuel == null) {
            throw new IllegalStateException("BuildcraftFuelRegistry.fuel is not wired");
        }
        Collection<IFuel> fuels = BuildcraftFuelRegistry.fuel.getFuels();
        if (fuels.size() < 9) {
            throw new IllegalStateException("Expected >= 9 combustion fuels for the JEI category, found " + fuels.size());
        }

        int dirtyWithResidue = 0;
        long maxPower = 0;
        for (IFuel fuel : fuels) {
            FluidStack fluid = fuel.getFluid();
            if (fluid == null || fluid.isEmpty()) {
                throw new IllegalStateException("Combustion fuel has an empty input fluid");
            }
            if (fuel.getPowerPerCycle() <= 0) {
                throw new IllegalStateException("Combustion fuel has non-positive power: " + fuel.getPowerPerCycle());
            }
            if (fuel.getTotalBurningTime() <= 0) {
                throw new IllegalStateException("Combustion fuel has non-positive burn time: " + fuel.getTotalBurningTime());
            }
            maxPower = Math.max(maxPower, fuel.getPowerPerCycle());
            if (fuel instanceof IDirtyFuel dirty) {
                FluidStack residue = dirty.getResidue();
                if (residue != null && !residue.isEmpty()) {
                    dirtyWithResidue++;
                }
            }
        }
        if (dirtyWithResidue < 3) {
            throw new IllegalStateException("Expected >= 3 dirty fuels carrying residue, found " + dirtyWithResidue);
        }
        // fuel_gaseous is the 8 MJ/t headline fuel — pin the top rate so the category can never
        // silently display a wrong number.
        if (maxPower != 8 * MjAPI.MJ) {
            throw new IllegalStateException("Expected top combustion fuel rate of 8 MJ/t, found " + (maxPower / MjAPI.MJ) + " MJ/t");
        }

        if (BuildcraftFuelRegistry.coolant == null) {
            throw new IllegalStateException("BuildcraftFuelRegistry.coolant is not wired");
        }
        Collection<ICoolant> coolants = BuildcraftFuelRegistry.coolant.getCoolants();
        if (coolants.isEmpty()) {
            throw new IllegalStateException("Expected at least one fluid coolant (water) for the JEI category");
        }
        for (ICoolant coolant : coolants) {
            if (coolant.getRepresentativeFluid() == null || coolant.getRepresentativeFluid().isEmpty()) {
                throw new IllegalStateException("Fluid coolant has no representative fluid — JEI category would skip it");
            }
        }
        Collection<ISolidCoolant> solids = BuildcraftFuelRegistry.coolant.getSolidCoolants();
        if (solids.size() < 3) {
            throw new IllegalStateException("Expected >= 3 solid coolants (ice/packed/blue), found " + solids.size());
        }
        for (ISolidCoolant solid : solids) {
            if (solid.getRepresentativeStack() == null || solid.getRepresentativeStack().isEmpty()) {
                throw new IllegalStateException("Solid coolant has no representative stack — JEI category would skip it");
            }
        }

        helper.succeed();
    }
}
