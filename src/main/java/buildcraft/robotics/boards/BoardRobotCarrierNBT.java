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

/** The {@link BoardRobotCarrier} factory + descriptor, mirroring {@link BoardRobotEmptyNBT}. */
public class BoardRobotCarrierNBT extends RedstoneBoardRobotNBT {

    public static final BoardRobotCarrierNBT INSTANCE = new BoardRobotCarrierNBT();

    public static final String ID = "buildcraftunofficial:boardRobotCarrier";

    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/entity/robot_carrier.png");

    private BoardRobotCarrierNBT() {}

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced) {
        list.add(Component.translatable("buildcraft.boardRobotCarrier").getString());
        list.add(Component.translatable("buildcraft.boardRobotCarrier.desc").getString());
    }

    @Override
    public String getDisplayName() {
        return Component.translatable("buildcraft.boardRobotCarrier").getString();
    }

    @Override
    public String getItemModelLocation() {
        return ID;
    }

    @Override
    public RedstoneBoardRobot create(IRobotAccess robot) {
        return new BoardRobotCarrier(robot);
    }

    @Override
    public Object getRobotTexture() {
        return TEXTURE;
    }
}
