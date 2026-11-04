/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

/**
 * The platform layer: the seam between BuildCraft and the mod loader it runs on (#98; the standing design is
 * {@code docs/platform-layer.md}).
 *
 * <p><b>Contract rule.</b> The files directly in this package, and in its sub-packages other than the
 * per-loader ones, are loader-neutral contracts. They may name only the JDK, vanilla and other
 * {@code buildcraft.platform} types: never a loader type, and never a BuildCraft gameplay class. That is what lets
 * a node for another loader compile the contracts alone.
 *
 * <p><b>Binder rule.</b> {@link buildcraft.platform.Platform} has one static accessor per service, each reading a
 * {@code static final} field of its own lazy holder class, so reaching one service never loads another's
 * implementation. The holders hold the only loader directive common code is allowed to carry:
 * {@code //? if neoforge} builds the NeoForge implementation, {@code //? if fabric} the Fabric one.
 *
 * <p><b>Implementation-package rule.</b> Loader code lives in {@code buildcraft.platform.<loader>}, one package per
 * loader. Each of its files keeps its {@code package} line live and sits whole inside that loader's directive arm,
 * so on any other loader's node it is an empty compilation unit. An implementation reads loader state on every call
 * and never caches loader statics in its own static initialisers. Nothing outside the binder names an
 * implementation class.
 */
package buildcraft.platform;
