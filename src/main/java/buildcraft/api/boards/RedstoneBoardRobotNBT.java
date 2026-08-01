/* Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.boards;

import net.minecraft.nbt.CompoundTag;


import buildcraft.api.robots.IRobotAccess;

public abstract class RedstoneBoardRobotNBT extends RedstoneBoardNBT<IRobotAccess> {

    @Override
    public RedstoneBoardRobot create(CompoundTag nbt, IRobotAccess robot) {
        return create(robot);
    }

    public abstract RedstoneBoardRobot create(IRobotAccess robot);

    public abstract Object getRobotTexture();

}
