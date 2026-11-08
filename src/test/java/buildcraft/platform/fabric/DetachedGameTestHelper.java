/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/^*
 * A {@link GameTestHelper} with no game test behind it, for running a game-test body somewhere the game-test framework
 * is not: {@link BCFabricClientGameTest} runs the bodies that load client classes (which Fabric's dedicated game-test
 * server refuses) on its singleplayer server through one. It answers only what those bodies ask: the level, positions
 * relative to an origin, the assertions (an {@link AssertionError}) and success. Anything else reaches the missing
 * test and fails.
 *^/
public final class DetachedGameTestHelper extends GameTestHelper {
    private final ServerLevel level;
    private final BlockPos origin;
    private boolean succeeded;

    public DetachedGameTestHelper(ServerLevel level, BlockPos origin) {
        super(null);
        this.level = level;
        this.origin = origin;
    }

    /^* Whether the body called {@link #succeed}. *^/
    public boolean succeeded() {
        return succeeded;
    }

    @Override
    public ServerLevel getLevel() {
        return level;
    }

    @Override
    public BlockPos absolutePos(BlockPos relative) {
        return origin.offset(relative);
    }

    @Override
    public void succeed() {
        succeeded = true;
    }

    @Override
    public void fail(String message) {
        throw new AssertionError(message);
    }

    // 1.21.1's helper has only the String forms, 1.21.10's assertTrue only the Component one.
    //? if >=1.21.10 {
    @Override
    public void fail(Component message) {
        throw new AssertionError(message.getString());
    }

    @Override
    public void assertTrue(boolean condition, Component message) {
        if (!condition) {
            fail(message);
        }
    }
    //?}

    //? if >=1.21.11 || <1.21.10 {
    @Override
    public void assertTrue(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }
    //?}
}
*///?}
