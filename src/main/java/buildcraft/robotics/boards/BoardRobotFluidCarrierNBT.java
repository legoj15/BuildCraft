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

/** The {@link BoardRobotFluidCarrier} factory + descriptor, mirroring {@link BoardRobotCarrierNBT}. */
public class BoardRobotFluidCarrierNBT extends RedstoneBoardRobotNBT {

    public static final BoardRobotFluidCarrierNBT INSTANCE = new BoardRobotFluidCarrierNBT();

    public static final String ID = "buildcraftunofficial:boardRobotFluidCarrier";

    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/entity/robot_fluid_carrier.png");

    private BoardRobotFluidCarrierNBT() {}

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced) {
        list.add(Component.translatable("buildcraft.boardRobotFluidCarrier").getString());
        list.add(Component.translatable("buildcraft.boardRobotFluidCarrier.desc").getString());
    }

    @Override
    public String getDisplayName() {
        return Component.translatable("buildcraft.boardRobotFluidCarrier").getString();
    }

    @Override
    public String getItemModelLocation() {
        return ID;
    }

    @Override
    public RedstoneBoardRobot create(IRobotAccess robot) {
        return new BoardRobotFluidCarrier(robot);
    }

    @Override
    public Object getRobotTexture() {
        return TEXTURE;
    }
}
