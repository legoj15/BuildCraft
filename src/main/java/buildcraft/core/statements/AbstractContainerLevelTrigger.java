/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.statements;

/**
 * Container-content triggers keyed by a fill-percentage {@link TriggerType} threshold, shared by
 * {@link TriggerInventoryLevel} (items) and {@link TriggerFluidContainerLevel} (fluids).
 * The {@code TriggerType} enum used to be declared byte-identically in both concrete classes.
 * All level triggers take exactly one optional item-filter parameter.
 */
public abstract class AbstractContainerLevelTrigger extends AbstractContainerContentTrigger {

    public final TriggerType type;

    protected AbstractContainerLevelTrigger(TriggerType type, String... uniqueTag) {
        super(uniqueTag);
        this.type = type;
    }

    @Override
    public int maxParameters() {
        return 1;
    }

    public enum TriggerType {
        BELOW25(0.25F),
        BELOW50(0.5F),
        BELOW75(0.75F);

        TriggerType(float level) {
            this.level = level;
        }

        public static final TriggerType[] VALUES = values();

        public final float level;
    }
}
