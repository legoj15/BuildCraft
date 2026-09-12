/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.robotics.boards;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.boards.RedstoneBoardRobot;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.robots.IRobotAccess;

/** The {@link BoardRobotShovelman} factory + descriptor, mirroring {@link BoardRobotLumberjackNBT}.
 *  The board id and robot-skin texture key off the board id so each board gets its own identity. */
public class BoardRobotShovelmanNBT extends RedstoneBoardRobotNBT {

    public static final BoardRobotShovelmanNBT INSTANCE = new BoardRobotShovelmanNBT();

    public static final String ID = "buildcraftunofficial:boardRobotShovelman";

    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/entity/robot_shovelman.png");

    private BoardRobotShovelmanNBT() {}

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced) {
        list.add(ChatFormatting.BOLD + Component.translatable("buildcraft.boardRobotShovelman").getString());
        list.add(Component.translatable("buildcraft.boardRobotShovelman.desc").getString());
    }

    @Override
    public String getDisplayName() {
        return Component.translatable("buildcraft.boardRobotShovelman").getString();
    }

    @Override
    public RedstoneBoardRobot create(IRobotAccess robot) {
        return new BoardRobotShovelman(robot);
    }

    @Override
    public Object getRobotTexture() {
        return TEXTURE;
    }
}
