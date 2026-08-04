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

/** The {@link BoardRobotPicker} factory + descriptor, mirroring {@link BoardRobotEmptyNBT}. The board id,
 *  item model location and robot-skin texture all key off the board id so each board gets its own identity. */
public class BoardRobotPickerNBT extends RedstoneBoardRobotNBT {

    public static final BoardRobotPickerNBT INSTANCE = new BoardRobotPickerNBT();

    public static final String ID = "buildcraftunofficial:boardRobotPicker";

    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/entity/robot_picker.png");

    private BoardRobotPickerNBT() {}

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced) {
        list.add(Component.translatable("buildcraft.boardRobotPicker").getString());
        list.add(Component.translatable("buildcraft.boardRobotPicker.desc").getString());
    }

    @Override
    public String getDisplayName() {
        return Component.translatable("buildcraft.boardRobotPicker").getString();
    }

    @Override
    public String getItemModelLocation() {
        return ID;
    }

    @Override
    public RedstoneBoardRobot create(IRobotAccess robot) {
        return new BoardRobotPicker(robot);
    }

    @Override
    public Object getRobotTexture() {
        return TEXTURE;
    }
}
