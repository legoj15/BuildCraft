/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
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

/** The {@link BoardRobotDelivery} factory + descriptor, mirroring {@link BoardRobotCarrierNBT}. */
public class BoardRobotDeliveryNBT extends RedstoneBoardRobotNBT {

    public static final BoardRobotDeliveryNBT INSTANCE = new BoardRobotDeliveryNBT();

    public static final String ID = "buildcraftunofficial:boardRobotDelivery";

    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/entity/robot_delivery.png");

    private BoardRobotDeliveryNBT() {}

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced) {
        list.add(Component.translatable("buildcraft.boardRobotDelivery").getString());
        list.add(Component.translatable("buildcraft.boardRobotDelivery.desc").getString());
    }

    @Override
    public String getDisplayName() {
        return Component.translatable("buildcraft.boardRobotDelivery").getString();
    }

    @Override
    public RedstoneBoardRobot create(IRobotAccess robot) {
        return new BoardRobotDelivery(robot);
    }

    @Override
    public Object getRobotTexture() {
        return TEXTURE;
    }
}
