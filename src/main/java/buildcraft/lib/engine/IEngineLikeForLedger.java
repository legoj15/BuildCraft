package buildcraft.lib.engine;

import net.minecraft.resources.Identifier;

import buildcraft.api.enums.EnumPowerStage;

/**
 * Yes, the name is bad. BC8 is nearly dead anyway.
 */
public interface IEngineLikeForLedger {

    EnumPowerStage getPowerStage();

    boolean isEngineOn();

    long getCurrentMjOutput();

    long getMjStored();

    double getHeat();

}
