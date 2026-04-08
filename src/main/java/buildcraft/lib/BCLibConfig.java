/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib;

import net.neoforged.neoforge.common.ModConfigSpec;

import buildcraft.api.mj.IMjToRfStatus;
import buildcraft.api.mj.MjRfConversion;

/**
 * Configuration file for lib. Uses NeoForge ModConfigSpec.
 */
public class BCLibConfig {

    public static ModConfigSpec.EnumValue<PowerMode> powerMode;
    public static ModConfigSpec.DoubleValue mjRfConversionAmount;

    public static void buildGeneral(ModConfigSpec.Builder builder) {
        powerMode = builder
                .comment("Power mode. Options are MJ_ONLY, MJ_AUTOCONVERT_RF, DISPLAY_RF")
                .defineEnum("powerMode", PowerMode.MJ_ONLY);

        mjRfConversionAmount = builder
                .comment("Conversion ratio for MJ <-> RF if autoconvert is enabled (MJ per RF)")
                .defineInRange("mjRfConversionAmount", 0.1, 0.0001, 0.2);
    }

    public enum PowerMode {
        /** MJ <-> RF conversion disabled, all machines require MJ exclusively to operate. */
        MJ_ONLY(false),
        /** MJ <-> RF conversion enabled, machines accept both MJ and RF. */
        MJ_AUTOCONVERT_RF(true),
        /** MJ <-> RF conversion enabled, machines accept both MJ and RF. Additionally machines will display power
         * amounts in RF rather than MJ. */
        DISPLAY_RF(true);

        public final boolean autoconvert;

        PowerMode(boolean autoconvert) {
            this.autoconvert = autoconvert;
        }
    }

    public static final class MjToRfStatus implements IMjToRfStatus {

        @Override
        public MjRfConversion getConversion() {
            return MjRfConversion.createParsed(BCLibConfig.mjRfConversionAmount.get());
        }

        @Override
        public boolean isAutoconvertEnabled() {
            return powerMode.get().autoconvert;
        }
    }
}
