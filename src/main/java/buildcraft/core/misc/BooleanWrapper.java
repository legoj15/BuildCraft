/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.misc;

/**
 * Temporary stub replacing buildcraft.api.expression.IVariableNodeBoolean until
 * the expression API is ported.
 */
public class BooleanWrapper {
    private boolean value;

    public BooleanWrapper(boolean defaultValue) {
        this.value = defaultValue;
    }

    public boolean evaluate() {
        return value;
    }

    public void set(boolean newValue) {
        this.value = newValue;
    }
}
