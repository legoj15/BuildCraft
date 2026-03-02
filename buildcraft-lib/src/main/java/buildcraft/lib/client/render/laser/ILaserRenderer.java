/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.laser;

import net.minecraft.resources.Identifier;

public interface ILaserRenderer {
    void vertex(double x, double y, double z, double u, double v, int lmap, float nx, float ny, float nz, float colour);
}
