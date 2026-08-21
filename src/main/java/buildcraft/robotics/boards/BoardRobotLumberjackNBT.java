/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.boards;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.IRobotAccess;

/** The {@link BoardRobotLumberjack} factory + descriptor, mirroring {@link BoardRobotPickerNBT}. The
 *  board id, item model location and robot-skin texture all key off the board id so each board gets
 *  its own identity. */
public class BoardRobotLumberjackNBT extends RedstoneBoardRobotNBT {

    public static final BoardRobotLumberjackNBT INSTANCE = new BoardRobotLumberjackNBT();

    public static final String ID = "buildcraftunofficial:boardRobotLumberjack";

    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/entity/robot_lumberjack.png");

    private BoardRobotLumberjackNBT() {}

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced) {
        list.add(Component.translatable("buildcraft.boardRobotLumberjack").getString());
        list.add(Component.translatable("buildcraft.boardRobotLumberjack.desc").getString());
    }

    @Override
    public String getDisplayName() {
        return Component.translatable("buildcraft.boardRobotLumberjack").getString();
    }

    @Override
    public String getItemModelLocation() {
        return ID;
    }

    @Override
    public RedstoneBoardRobot create(IRobotAccess robot) {
        return new BoardRobotLumberjack(robot);
    }

    @Override
    public Object getRobotTexture() {
        return TEXTURE;
    }
}
