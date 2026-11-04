/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.transfer;

/**
 * An open transaction as a storage operation sees it: it can only be nested into and asked for its depth. Storage
 * methods take this type, never {@link BCTransaction}, so a callee cannot commit or close its caller's transaction
 * (the same split as NeoForge's and Fabric's {@code TransactionContext}).
 */
public interface BCTransactionContext {
    /** The nesting depth: {@code 0} for a root transaction. */
    int depth();

    /** Opens a transaction nested in this one. It must be closed before this one is used again. */
    BCTransaction openNested();
}
