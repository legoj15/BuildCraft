/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.robotics.client.model;

//? if >=1.21.10 {
import net.minecraft.client.color.item.ItemTintSource;
//?}
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import buildcraft.robotics.item.ItemRobot;

/**
 * Tints the robot icon's 7.1.x eye decals by the stack's charge, so a charged robot reads
 * differently from a drained one at a glance — 7.1.x's inventory icon hardcoded the decal alpha
 * ({@code doRenderRobot(..., 0.9F, ...)}) so the square was always bright red there, while the
 * world robot's eyes faded with the battery.
 *
 * <p>The tinted quads are the decal element in {@code robot_chassis_base.json}: a 0.01-proud
 * duplicate of the chassis cube carrying the same face UVs, textured with the item copies of
 * 7.1.x's own decal textures. Tintindex 0 — the four side faces — is the red eye decal
 * ({@code robot_overlay_side.png}), tinted by this class with a neutral multiplier at the charge
 * fraction: 7.1.x modulated the decal's alpha ({@code glColor4f(1, 1, 1, storagePercent)} under
 * disabled lighting), and since item quads bake onto an alpha-cutout sheet with no blending, the
 * fade is made by multiplying the red toward black instead. Tintindex 1 — the bottom face — is
 * the pale-cyan under-port ({@code robot_overlay_bottom.png}), which 7.1.x drew at full alpha
 * whenever the robot was awake: constant white, so the decal art's own colour shows.
 *
 * <p>On every node from 1.21.10 up this class is declared as
 * {@code buildcraftunofficial:robot_charge} in the {@code tints} of every {@code items/robot.json}
 * model leaf (the constant is vanilla's {@code minecraft:constant} there); 1.21.1, which has no
 * data-driven tint sources, registers {@link #INSTANCE} as a classic {@code ItemColor} on the
 * robot item and answers both tintindexes itself (both from {@code BCRoboticsClient}). The colour
 * computation is shared either way: {@code ItemRobot#chargeEyeTintArgb}.
 */
//? if >=1.21.10 {
public final class RobotChargeTintSource implements ItemTintSource {
//?} else {
/*public final class RobotChargeTintSource implements net.minecraft.client.color.item.ItemColor {*/
//?}
    public static final RobotChargeTintSource INSTANCE = new RobotChargeTintSource();
    //? if >=1.21.10 {
    public static final MapCodec<RobotChargeTintSource> MAP_CODEC = MapCodec.unit(INSTANCE);
    //?}

    private RobotChargeTintSource() {}

    /** Shared colour computation; modern {@code calculate} and 1.21.1 {@code getColor} both delegate.
     *  The empty-board template stays dark whatever its blob claims: it is not a functional robot, so
     *  the icon reads as the inert plain chassis — matching the charge line and the inventory bar,
     *  which it also does not get ({@code ItemRobot#hasEmptyBoard} is the shared rule). */
    private int computeColor(ItemStack stack) {
        if (ItemRobot.hasEmptyBoard(stack)) {
            return ItemRobot.chargeEyeTintArgb(0L);
        }
        return ItemRobot.chargeEyeTintArgb(ItemRobot.getEnergy(stack));
    }

    //? if >=1.21.10 {
    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
        return computeColor(stack);
    }

    @Override
    public MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }
    //?} else {
    /*@Override
    public int getColor(ItemStack stack, int tintIndex) {
        // tintindex 0 is the charge-faded red eye decal; tintindex 1 is the always-on under-port —
        // white, so the decal art's own pale cyan shows (the 1.21.10+ nodes declare that pass as a
        // vanilla minecraft:constant tint instead).
        return tintIndex == 0 ? computeColor(stack) : -1;
    }*/
    //?}
}
