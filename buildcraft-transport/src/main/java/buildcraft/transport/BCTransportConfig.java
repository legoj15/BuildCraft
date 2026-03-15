/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.transport;

/** Transport module configuration. Currently stubbed — values will be loaded from
 *  a NeoForge config file when the config system is ported. */
public class BCTransportConfig {
    public static boolean disableRfPipe = false;
    public static boolean powerPipeUseOldMjTexture = false;

    /** MJ cost per item extracted by a wooden pipe. Default: 1 MJ (= 1_000_000 µMJ). */
    public static long mjPerItem = 1_000_000L;

    /** MJ cost per millibucket extracted by a wooden fluid pipe. Default: 1000 µMJ per mB. */
    public static long mjPerMillibucket = 1_000L;
}
