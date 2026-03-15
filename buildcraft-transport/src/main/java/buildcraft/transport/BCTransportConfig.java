/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.transport.pipe.PipeApi;
import buildcraft.api.transport.pipe.PipeApi.PowerTransferInfo;
import buildcraft.api.transport.pipe.PipeDefinition;

/** Transport module configuration. Currently stubbed — values will be loaded from
 *  a NeoForge config file when the config system is ported. */
public class BCTransportConfig {
    public static boolean disableRfPipe = false;
    public static boolean powerPipeUseOldMjTexture = false;

    /** MJ cost per item extracted by a wooden pipe. Default: 1 MJ (= 1_000_000 µMJ). */
    public static long mjPerItem = 1_000_000L;

    /** MJ cost per millibucket extracted by a wooden fluid pipe. Default: 1000 µMJ per mB. */
    public static long mjPerMillibucket = 1_000L;

    /** Base multiplier for kinesis pipe transfer rates (MJ). Default: 4. */
    public static int basePowerRate = 4;

    /** Register default power transfer info for all kinesis pipe definitions.
     *  Should be called after BCTransportPipes.preInit(). */
    public static void registerPowerTransferData() {
        powerTransfer(BCTransportPipes.cobblePower, basePowerRate, 16, false);
        powerTransfer(BCTransportPipes.stonePower, basePowerRate * 2, 32, false);
        powerTransfer(BCTransportPipes.woodPower, basePowerRate * 4, 128, true);
        powerTransfer(BCTransportPipes.sandstonePower, basePowerRate * 4, 32, false);
        powerTransfer(BCTransportPipes.quartzPower, basePowerRate * 8, 32, false);
        powerTransfer(BCTransportPipes.ironPower, basePowerRate * 8, 32, false);
        powerTransfer(BCTransportPipes.goldPower, basePowerRate * 32, 32, false);
        powerTransfer(BCTransportPipes.diamondPower, basePowerRate * 64, 32, false);
        powerTransfer(BCTransportPipes.diaWoodPower, basePowerRate * 64, 32, true);
    }

    private static void powerTransfer(PipeDefinition def, int transferMultiplier, int resistanceDivisor, boolean recv) {
        long transfer = MjAPI.MJ * transferMultiplier;
        long resistance = MjAPI.MJ / resistanceDivisor;
        PipeApi.powerTransferData.put(def, PowerTransferInfo.createFromResistance(transfer, resistance, recv));
    }
}
