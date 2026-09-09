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

/**
 * The {@link BoardRobotEmpty} factory + descriptor: the id an unknown board falls back to, and the texture a
 * robot wearing it is drawn with.
 *
 * <p>A singleton because {@code RedstoneBoardRobotNBT} carries no per-instance state and both the registry and
 * {@link BoardRobotEmpty#getNBTHandler()} need the same object back.
 *
 * <p>The texture is a DIRECT FILE PATH, not an atlas sprite: entity textures are bound whole, so
 * {@code SpriteHolderRegistry} does not apply here and using it would silently produce the missing-texture
 * chequerboard. Written in the canonical {@code Identifier} form — the build rewrites it for the nodes that
 * still call the class {@code ResourceLocation}.
 */
public class BoardRobotEmptyNBT extends RedstoneBoardRobotNBT {

    public static final BoardRobotEmptyNBT INSTANCE = new BoardRobotEmptyNBT();

    /** Namespaced so a board id is unambiguous across mods, exactly as 7.1.x's {@code BCBoardNBT} ids were. */
    public static final String ID = "buildcraftunofficial:empty_robot_board";

    private static final Identifier TEXTURE =
            Identifier.parse("buildcraftunofficial:textures/entity/robot_base.png");

    private BoardRobotEmptyNBT() {}

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public void addInformation(ItemStack stack, Player player, List<String> list, boolean advanced) {
        list.add(Component.translatable("buildcraft.boardRobotEmpty").getString());
    }

    @Override
    public String getDisplayName() {
        return Component.translatable("buildcraft.boardRobotEmpty").getString();
    }

    @Override
    public RedstoneBoardRobot create(IRobotAccess robot) {
        return new BoardRobotEmpty(robot);
    }

    /** Typed {@code Object} by the API (it predates any shared resource type); every consumer casts it back to
     *  an {@code Identifier}. */
    @Override
    public Object getRobotTexture() {
        return TEXTURE;
    }
}
