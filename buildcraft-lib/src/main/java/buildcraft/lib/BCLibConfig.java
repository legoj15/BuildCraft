/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib;

import buildcraft.api.mj.IMjToRfStatus;
import buildcraft.api.mj.MjRfConversion;

/**
 * Configuration file for lib. Minimal port — only contains fields needed at runtime.
 * The full config system from 1.12.2 is not yet ported.
 */
public class BCLibConfig {

    /** MJ to RF conversion ratio. */
    public static MjRfConversion mjRfConversion = MjRfConversion.createDefault();

    public static PowerMode powerMode = PowerMode.MJ_ONLY;

    public enum PowerMode {
        /** MJ &lt;-&gt; RF conversion disabled, all machines require MJ exclusively to operate. */
        MJ_ONLY(false),
        /** MJ &lt;-&gt; RF conversion enabled, machines accept both MJ and RF. */
        MJ_AUTOCONVERT_RF(true),
        /** MJ &lt;-&gt; RF conversion enabled, machines accept both MJ and RF. Additionally machines will display power
         * amounts in RF rather than MJ. */
        DISPLAY_RF(true);

        final boolean autoconvert;

        PowerMode(boolean autoconvert) {
            this.autoconvert = autoconvert;
        }
    }

    public static final class MjToRfStatus implements IMjToRfStatus {

        @Override
        public MjRfConversion getConversion() {
            return BCLibConfig.mjRfConversion;
        }

        @Override
        public boolean isAutoconvertEnabled() {
            return powerMode.autoconvert;
        }
    }
}
