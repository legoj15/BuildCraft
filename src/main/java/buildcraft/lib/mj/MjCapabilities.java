/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.mj;

import java.util.function.Function;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;
import buildcraft.platform.transfer.BCEnergyStorage;
import buildcraft.platform.transfer.CapabilityRegistrar;

/**
 * Registration-side helper that bundles the standard MJ-consumer capability triple ~9 BuildCraft machines
 * expose, so a machine's capability registration is one call instead of three hand-copied
 * registrations. Hand-copying had already drifted once — the builders machines
 * were missing {@code CAP_CONNECTOR} — which this makes structurally impossible.
 */
public final class MjCapabilities {
    private MjCapabilities() {}

    /**
     * Registers {@code CAP_RECEIVER} + {@code CAP_CONNECTOR} (both backed by the machine's
     * {@link IMjReceiver}; a receiver <i>is</i> a connector) plus the Forge-Energy storage.
     *
     * <p>The FE storage is produced by {@code energyFactory}, which MUST route through
     * {@link MjBatteryEnergyHandler#createIfRfEnabled} so the exposure stays gated on the {@code powerMode}
     * config (it returns {@code null} under {@code MJ_ONLY}, so no FE cable connects).
     *
     * @param receiver      the machine's MJ receiver getter (a redstone or gated {@link IMjReceiver} subtype
     *                      is fine — the two MJ caps take the supertype).
     * @param energyFactory produces the FE storage for a machine instance, e.g.
     *                      {@code m -> MjBatteryEnergyHandler.createIfRfEnabled(m.getBattery())}, or with a
     *                      gate for machines that refuse FE while idle (the quarry).
     */
    public static <BE extends BlockEntity> void registerMjConsumer(
            CapabilityRegistrar registrar,
            BlockEntityType<BE> type,
            Function<BE, ? extends IMjReceiver> receiver,
            Function<BE, ? extends BCEnergyStorage> energyFactory) {
        registrar.block(MjAPI.CAP_RECEIVER, type, (be, dir) -> receiver.apply(be));
        registrar.block(MjAPI.CAP_CONNECTOR, type, (be, dir) -> receiver.apply(be));
        registrar.blockEnergy(type, (be, side) -> energyFactory.apply(be));
    }
}
