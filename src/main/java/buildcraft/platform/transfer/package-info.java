/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

/**
 * The transfer contract: transactions and snapshot journals, then BuildCraft's storage views and capability lookup
 * (epic #98, S14). Reached through {@code buildcraft.platform.Platform#transfer()}. Contract files only: JDK, vanilla,
 * {@code buildcraft.api.fluids} and {@code buildcraft.api.capability} types.
 *
 * <p>A {@link buildcraft.platform.transfer.BCTransaction} is the loader's own transaction underneath (NeoForge
 * {@code Transaction} on 1.21.10+, Fabric {@code Transaction}). On 1.21.1, which has no transfer transactions, a small
 * BuildCraft-owned manager stands in, and the classic handlers' simulate flag maps onto "open a root, commit only to
 * execute". A storage changes its state only after {@code updateSnapshots} on its
 * {@link buildcraft.platform.transfer.BCSnapshotJournal}, so an aborted transaction rolls it back.
 */
package buildcraft.platform.transfer;
