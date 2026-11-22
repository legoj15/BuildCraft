/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.test;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.logging.LogUtils;

import net.minecraft.gametest.framework.GameTestHelper;

import org.slf4j.Logger;

/**
 * An opt-in log of every game test's start and end ({@code -Dbuildcraft.gameTestTrace=true}; the Forge node's
 * {@code -PbcGameTestTrace}), for the one question the framework does not answer: which tests were running when the
 * server died. A product exception in a level tick (a null level in saved data, say) crashes the whole run and the crash
 * report names no test; the "start" lines with no "end" line after them are the suspects. Forge's {@code GameTestHelper}
 * has {@code addCleanup}, which runs once the test is done, pass or fail, so only there is the end logged; elsewhere the
 * starts alone are. A failure thrown from a test body is logged with its stack as well (the framework prints the message
 * alone); one thrown from a later tick is not seen here.
 */
public final class GameTestTrace {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ON = Boolean.getBoolean("buildcraft.gameTestTrace");

    private GameTestTrace() {}

    /** {@code body} itself, or (with the trace on) a body that logs the test's start and, where it can, its end. */
    public static Supplier<Consumer<GameTestHelper>> wrap(String id, Supplier<Consumer<GameTestHelper>> body) {
        if (!ON) {
            return body;
        }
        return () -> {
            Consumer<GameTestHelper> test = body.get();
            return helper -> {
                LOGGER.info("[gametest-trace] start {}", id);
                //? if forge {
                /*helper.addCleanup(passed -> LOGGER.info("[gametest-trace] end {} {}", id, passed ? "pass" : "fail"));*/
                //?}
                try {
                    test.accept(helper);
                } catch (RuntimeException | Error e) {
                    // The framework reports a failure by its message alone; a failure thrown from the body has a stack worth reading.
                    LOGGER.error("[gametest-trace] failed {}", id, e);
                    throw e;
                }
            };
        };
    }
}
