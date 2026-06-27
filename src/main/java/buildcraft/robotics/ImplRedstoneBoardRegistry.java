/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.NbtApiUtil;

/**
 * Concrete {@link RedstoneBoardRegistry}, ported from 7.1.x {@code buildcraft.robotics.ImplRedstoneBoardRegistry}.
 * Maps board ids to their {@link RedstoneBoardNBT} factory and MJ cost. The 7.1.x {@code int} energy cost is now
 * the API's {@code long} micro-MJ price; the icon-registration and probability-cost helpers (gone from the modern
 * API) are dropped, and an unknown id falls back to the empty robot board exactly as before.
 */
public class ImplRedstoneBoardRegistry extends RedstoneBoardRegistry {

    private static class BoardFactory {
        RedstoneBoardNBT<?> boardNBT;
        long microJoules;
    }

    private final Map<String, BoardFactory> boards = new HashMap<>();
    private RedstoneBoardRobotNBT emptyRobotBoardNBT;

    @Override
    public void registerBoardType(RedstoneBoardNBT<?> redstoneBoardNBT, long microJoules) {
        BoardFactory factory = new BoardFactory();
        factory.boardNBT = redstoneBoardNBT;
        factory.microJoules = microJoules;
        boards.put(redstoneBoardNBT.getID(), factory);
    }

    @Override
    public void setEmptyRobotBoard(RedstoneBoardRobotNBT redstoneBoardNBT) {
        emptyRobotBoardNBT = redstoneBoardNBT;
    }

    @Override
    public RedstoneBoardRobotNBT getEmptyRobotBoard() {
        return emptyRobotBoardNBT;
    }

    @Override
    public RedstoneBoardNBT<?> getRedstoneBoard(CompoundTag nbt) {
        return getRedstoneBoard(NbtApiUtil.getString(nbt, "id", ""));
    }

    @Override
    public RedstoneBoardNBT<?> getRedstoneBoard(String id) {
        BoardFactory factory = boards.get(id);
        if (factory != null) {
            return factory.boardNBT;
        } else {
            return emptyRobotBoardNBT;
        }
    }

    @Override
    public Collection<RedstoneBoardNBT<?>> getAllBoardNBTs() {
        ArrayList<RedstoneBoardNBT<?>> result = new ArrayList<>();
        for (BoardFactory f : boards.values()) {
            result.add(f.boardNBT);
        }
        return result;
    }

    @Override
    public long getPowerCost(RedstoneBoardNBT<?> board) {
        BoardFactory factory = boards.get(board.getID());
        return factory != null ? factory.microJoules : 0L;
    }
}
