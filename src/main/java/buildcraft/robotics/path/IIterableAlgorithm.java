/*
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.path;

/**
 * An algorithm that makes incremental progress in bounded steps so it can be amortised across game
 * ticks rather than blocking a tick until it completes. Ported from 7.1.x
 * {@code buildcraft.core.lib.utils.IIterableAlgorithm}.
 */
public interface IIterableAlgorithm {

    /** Advance the algorithm by one default-sized batch of work. */
    void iterate();

    /** @return true once no further progress is possible (a result is ready, or the search is exhausted). */
    boolean isDone();
}
