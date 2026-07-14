/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.mj;

import java.util.function.Function;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import buildcraft.api.mj.IMjReceiver;
import buildcraft.api.mj.MjAPI;

/**
 * Registration-side helper that bundles the standard MJ-consumer capability triple ~9 BuildCraft machines
 * expose, so a machine's capability registration is one call instead of three hand-copied
 * {@code event.registerBlockEntity} lines. Hand-copying had already drifted once — the builders machines
 * were missing {@code CAP_CONNECTOR} — which this makes structurally impossible.
 */
public final class MjCapabilities {
    private MjCapabilities() {}

    /**
     * Registers {@code CAP_RECEIVER} + {@code CAP_CONNECTOR} (both backed by the machine's
     * {@link IMjReceiver}; a receiver <i>is</i> a connector) plus the Forge-Energy capability.
     *
     * <p>The FE handler is produced by {@code energyFactory}, which MUST route through
     * {@link MjBatteryEnergyHandler#createIfRfEnabled} so the exposure stays gated on the {@code powerMode}
     * config (it returns {@code null} under {@code MJ_ONLY}, so no FE cable connects). {@code energyCap} is
     * the version-neutral energy token the caller resolves ({@code Capabilities.Energy.BLOCK} on 1.21.10+,
     * {@code Capabilities.EnergyStorage.BLOCK} on 1.21.1).
     *
     * @param receiver      the machine's MJ receiver getter (a redstone or gated {@link IMjReceiver} subtype
     *                      is fine — the two MJ caps take the supertype).
     * @param energyFactory produces the FE handler for a machine instance, e.g.
     *                      {@code m -> MjBatteryEnergyHandler.createIfRfEnabled(m.getBattery())}, or with a
     *                      gate for machines that refuse FE while idle (the quarry).
     */
    public static <BE extends BlockEntity, C, E> void registerMjConsumer(
            RegisterCapabilitiesEvent event,
            BlockEntityType<BE> type,
            Function<BE, ? extends IMjReceiver> receiver,
            BlockCapability<E, C> energyCap,
            Function<BE, ? extends E> energyFactory) {
        event.registerBlockEntity(MjAPI.CAP_RECEIVER, type, (be, dir) -> receiver.apply(be));
        event.registerBlockEntity(MjAPI.CAP_CONNECTOR, type, (be, dir) -> receiver.apply(be));
        event.registerBlockEntity(energyCap, type, (be, ctx) -> energyFactory.apply(be));
    }
}
