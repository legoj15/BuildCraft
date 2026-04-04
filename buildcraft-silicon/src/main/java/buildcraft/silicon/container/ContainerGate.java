/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.container;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import buildcraft.api.core.BCLog;
import buildcraft.api.core.EnumPipePart;
import buildcraft.api.statements.StatementManager;
import buildcraft.api.transport.pipe.IPipeHolder;

import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.StatementWrapper;
import buildcraft.lib.statement.TriggerWrapper;

import buildcraft.silicon.gate.GateContext;
import buildcraft.silicon.gate.GateContext.GateGroup;
import buildcraft.silicon.gate.GateLogic;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.silicon.BCSiliconMenuTypes;

public class ContainerGate extends ContainerBC_Neptune {
    public static final int ID_CONNECTION = 1;
    public static final int ID_VALID_STATEMENTS = 2;

    public final GateLogic gate;
    public final IPipeHolder pipeHolder;

    public final int slotHeight;

    public final SortedSet<TriggerWrapper> possibleTriggers;
    public final SortedSet<ActionWrapper> possibleActions;

    public final GateContext<TriggerWrapper> possibleTriggersContext;
    public final GateContext<ActionWrapper> possibleActionsContext;

    // Client-side network constructor
    public ContainerGate(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getPluggableGate(playerInv, buf));
    }

    private static PluggableGate getPluggableGate(Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction side = buf.readEnum(Direction.class);
        if (inv.player.level() != null) {
            var be = inv.player.level().getBlockEntity(pos);
            if (be instanceof buildcraft.transport.tile.TilePipeHolder holder) {
                var plug = holder.getPluggable(side);
                if (plug instanceof PluggableGate gatePlug) {
                    return gatePlug;
                }
            }
        }
        throw new IllegalStateException("No Gate pluggable at " + pos + " side " + side);
    }

    // Server-side constructor
    public ContainerGate(int containerId, Inventory playerInv, PluggableGate pluggable) {
        super(BCSiliconMenuTypes.GATE.get(), containerId, playerInv.player);
        this.gate = pluggable.logic;
        this.pipeHolder = pluggable.holder;

        // Register opening
        this.pipeHolder.onPlayerOpen(player);

        boolean split = gate.isSplitInTwo();
        int s = gate.variant.numSlots;
        if (split) {
            s = (int) Math.ceil(s / 2.0);
        }
        slotHeight = s;

        if (this.pipeHolder.getPipeWorld().isClientSide()) {
            possibleTriggers = new TreeSet<>();
            possibleActions = new TreeSet<>();
        } else {
            possibleTriggers = gate.getAllValidTriggers();
            possibleActions = gate.getAllValidActions();
        }

        possibleTriggersContext = new GateContext<>(new ArrayList<>());
        possibleActionsContext = new GateContext<>(new ArrayList<>());

        refreshPossibleGroups();

        addFullPlayerInventory(8, 33 + slotHeight * 18);
    }

    @Override
    public boolean stillValid(Player player) {
        return pipeHolder.canPlayerInteract(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        pipeHolder.onPlayerClose(player);
    }

    private void refreshPossibleGroups() {
        refresh(possibleActions, possibleActionsContext);
        refresh(possibleTriggers, possibleTriggersContext);
    }

    private static <T extends StatementWrapper> void refresh(SortedSet<T> from, GateContext<T> to) {
        to.groups.clear();
        Map<EnumPipePart, List<T>> parts = new EnumMap<>(EnumPipePart.class);
        for (T val : from) {
            parts.computeIfAbsent(val.sourcePart, p -> new ArrayList<>()).add(val);
        }
        List<T> list = parts.get(EnumPipePart.CENTER);
        if (list == null) {
            list = new ArrayList<>(1);
            list.add(null);
        } else {
            list.add(0, null);
        }
        to.groups.add(new GateGroup<>(EnumPipePart.CENTER, list));
        for (EnumPipePart part : EnumPipePart.FACES) {
            list = parts.get(part);
            if (list != null) {
                to.groups.add(new GateGroup<>(part, list));
            }
        }
    }

    @Override
    public void readMessage(int id, PacketBufferBC buffer, boolean isClient, IPayloadContext ctx) {
        super.readMessage(id, buffer, isClient, ctx);
        if (!isClient) {
            if (id == ID_CONNECTION) {
                int index = buffer.readUnsignedByte();
                boolean to = buffer.readBoolean();
                if (index < gate.connections.length) {
                    gate.connections[index] = to;
                    gate.sendResolveData();
                }
            } else if (id == ID_VALID_STATEMENTS) {
                // Client asked for valid statements, send them
                sendStatementsToClient();
            }
        } else {
            if (id == ID_VALID_STATEMENTS) {
                possibleTriggers.clear();
                possibleActions.clear();
                int numTriggers = buffer.readInt();
                int numActions = buffer.readInt();
                for (int i = 0; i < numTriggers; i++) {
                    String tag = buffer.readUtf();
                    EnumPipePart part = buffer.readEnumValue(EnumPipePart.class);
                    var state = StatementManager.statements.get(tag);
                    if (state == null) {
                        BCLog.logger.warn("Gate received invalid trigger tag from server: " + tag);
                        continue;
                    }
                    TriggerWrapper wrapper = TriggerWrapper.wrap(state, part.face);
                    if (gate.isValidTrigger(wrapper)) {
                        possibleTriggers.add(wrapper);
                    }
                }
                for (int i = 0; i < numActions; i++) {
                    String tag = buffer.readUtf();
                    EnumPipePart part = buffer.readEnumValue(EnumPipePart.class);
                    var state = StatementManager.statements.get(tag);
                    if (state == null) {
                        BCLog.logger.warn("Gate received invalid action tag: " + tag);
                        continue;
                    }
                    ActionWrapper wrapper = ActionWrapper.wrap(state, part.face);
                    if (gate.isValidAction(wrapper)) {
                        possibleActions.add(wrapper);
                    }
                }
                refreshPossibleGroups();
            }
        }
    }

    private void sendStatementsToClient() {
        sendMessage(ID_VALID_STATEMENTS, (buffer) -> {
            buffer.writeInt(possibleTriggers.size());
            buffer.writeInt(possibleActions.size());
            for (TriggerWrapper wrapper : possibleTriggers) {
                buffer.writeUtf(wrapper.getUniqueTag());
                buffer.writeEnum(wrapper.sourcePart);
            }

            for (ActionWrapper wrapper : possibleActions) {
                buffer.writeUtf(wrapper.getUniqueTag());
                buffer.writeEnum(wrapper.sourcePart);
            }

            // Sync connections
            for (int i = 0; i < gate.connections.length; i++) {
                buffer.writeBoolean(gate.connections[i]);
            }
        });
    }

    public void requestValidStatements() {
        sendMessage(ID_VALID_STATEMENTS, buffer -> {});
    }

    public void setConnected(int index, boolean to) {
        sendMessage(ID_CONNECTION, (buffer) -> {
            buffer.writeByte(index);
            buffer.writeBoolean(to);
        });
    }
}
