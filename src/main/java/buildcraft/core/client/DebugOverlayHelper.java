/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

//? if >=1.21.10 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}

import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.debug.ClientDebuggables;
import buildcraft.lib.net.MessageDebugRequest;

/**
 * Client-side logic for the F3 debug overlay and the periodic server request.
 * <p>
 * This is called from a client tick handler. Each tick it checks whether the
 * debug screen is open and the crosshair is over an {@link IDebuggable}. If
 * so it sends a {@link MessageDebugRequest} to the server and collects the
 * client-side debug strings for display.
 */
public class DebugOverlayHelper {

    private static final String DIFF_START = ChatFormatting.RED + "" + ChatFormatting.BOLD + "!" + ChatFormatting.RESET;
    private static final String DIFF_HEADER_FORMATTING = ChatFormatting.AQUA + "" + ChatFormatting.BOLD;

    // Cached client-side data, refreshed every tick
    private static final List<String> CLIENT_LEFT = new ArrayList<>();
    private static final List<String> CLIENT_RIGHT = new ArrayList<>();
    private static Direction lastSide = Direction.UP;
    private static boolean hasData = false;

    /** Called from ClientTickEvent.Post every tick. */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        HitResult mouseOver = mc.hitResult;

        IDebuggable debuggable = ClientDebuggables.getDebuggableObject(mouseOver);
        if (debuggable == null) {
            if (hasData) {
                CLIENT_LEFT.clear();
                CLIENT_RIGHT.clear();
                ClientDebuggables.SERVER_LEFT.clear();
                ClientDebuggables.SERVER_RIGHT.clear();
                hasData = false;
            }
            return;
        }

        hasData = true;
        Direction side = ClientDebuggables.getHitSide(mouseOver);
        if (side == null) side = Direction.UP;
        lastSide = side;

        // Collect client-side debug info
        CLIENT_LEFT.clear();
        CLIENT_RIGHT.clear();
        debuggable.getDebugInfo(CLIENT_LEFT, CLIENT_RIGHT, side);

        // Send request to server for server-side data
        if (mouseOver instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            //? if >=1.21.10 {
            ClientPacketDistributor.sendToServer(new MessageDebugRequest(pos, side));
            //?} else {
            /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(new MessageDebugRequest(pos, side));*/
            //?}
        }
    }

    /**
     * Collects all debug lines for the left side of the F3 overlay.
     * Returns an empty list if no data is available.
     */
    public static List<String> getLeftLines() {
        if (!hasData) return List.of();
        List<String> result = new ArrayList<>();
        appendDiff(result, ClientDebuggables.SERVER_LEFT, CLIENT_LEFT,
                DIFF_HEADER_FORMATTING + "SERVER:", DIFF_HEADER_FORMATTING + "CLIENT:");

        // Also collect any client-only debug info
        Minecraft mc = Minecraft.getInstance();
        IDebuggable debuggable = ClientDebuggables.getDebuggableObject(mc.hitResult);
        if (debuggable != null) {
            debuggable.getClientDebugInfo(result, new ArrayList<>(), lastSide);
        }
        return result;
    }

    /**
     * Collects all debug lines for the right side of the F3 overlay.
     * Returns an empty list if no data is available.
     */
    public static List<String> getRightLines() {
        if (!hasData) return List.of();
        List<String> result = new ArrayList<>();
        appendDiff(result, ClientDebuggables.SERVER_RIGHT, CLIENT_RIGHT,
                DIFF_HEADER_FORMATTING + "SERVER:", DIFF_HEADER_FORMATTING + "CLIENT:");

        // Also collect any client-only debug info for right side
        Minecraft mc = Minecraft.getInstance();
        IDebuggable debuggable = ClientDebuggables.getDebuggableObject(mc.hitResult);
        if (debuggable != null) {
            List<String> leftDummy = new ArrayList<>();
            debuggable.getClientDebugInfo(leftDummy, result, lastSide);
        }
        return result;
    }

    private static void appendDiff(List<String> dest, List<String> first, List<String> second,
                                   String headerFirst, String headerSecond) {
        dest.add("");
        dest.add(headerFirst);
        dest.addAll(first);
        dest.add("");
        dest.add(headerSecond);
        if (first.size() != second.size()) {
            // Different sizes — no diffing possible
            dest.addAll(second);
        } else {
            for (int l = 0; l < first.size(); l++) {
                String shownLine = first.get(l);
                String diffLine = second.get(l);
                if (shownLine.equals(diffLine)) {
                    dest.add(diffLine);
                } else {
                    if (diffLine.startsWith(" ")) {
                        dest.add(DIFF_START + diffLine.substring(1));
                    } else {
                        dest.add(DIFF_START + diffLine);
                    }
                }
            }
        }
    }
}
