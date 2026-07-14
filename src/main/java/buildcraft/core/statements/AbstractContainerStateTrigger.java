/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

/**
 * Container-content triggers keyed by a coarse {@link State} (empty / contains / space / full),
 * shared by {@link TriggerInventory} (items) and {@link TriggerFluidContainer} (fluids).
 * The {@code State} enum used to be declared byte-identically in both concrete classes.
 */
public abstract class AbstractContainerStateTrigger extends AbstractContainerContentTrigger {

    public final State state;

    protected AbstractContainerStateTrigger(State state, String... uniqueTag) {
        super(uniqueTag);
        this.state = state;
    }

    @Override
    public int maxParameters() {
        return state == State.CONTAINS || state == State.SPACE ? 1 : 0;
    }

    public enum State {
        EMPTY,
        CONTAINS,
        SPACE,
        FULL;

        public static final State[] VALUES = values();
    }
}
