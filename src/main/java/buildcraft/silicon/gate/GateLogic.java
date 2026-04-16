package buildcraft.silicon.gate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;

import buildcraft.api.BCModules;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.InvalidInputDataException;
import buildcraft.api.gates.IGate;
import buildcraft.api.statements.IActionExternal;
import buildcraft.api.statements.IActionInternal;
import buildcraft.api.statements.IActionInternalSided;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.ITriggerInternal;
import buildcraft.api.statements.ITriggerInternalSided;
import buildcraft.api.statements.StatementManager;
import buildcraft.api.statements.StatementSlot;
import buildcraft.api.statements.containers.IRedstoneStatementContainer;
import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.IWireManager;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pipe.PipeEvent;
import buildcraft.api.transport.pipe.PipeEventActionActivate;

import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.IdAllocator;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.statement.ActionWrapper;
import buildcraft.lib.statement.ActionWrapper.ActionWrapperExternal;
import buildcraft.lib.statement.ActionWrapper.ActionWrapperInternal;
import buildcraft.lib.statement.ActionWrapper.ActionWrapperInternalSided;
import buildcraft.lib.statement.FullStatement;
import buildcraft.lib.statement.FullStatement.IStatementChangeListener;
import buildcraft.lib.statement.TriggerWrapper;
import buildcraft.lib.statement.TriggerWrapper.TriggerWrapperExternal;
import buildcraft.lib.statement.TriggerWrapper.TriggerWrapperInternal;
import buildcraft.lib.statement.TriggerWrapper.TriggerWrapperInternalSided;

import buildcraft.silicon.plug.PluggableGate;

public class GateLogic implements IGate, IWireEmitter, IRedstoneStatementContainer {

    protected static final IdAllocator ID_ALLOC = new IdAllocator("GateLogic");

    /** Sent when any of {@link #triggerOn}, {@link #actionOn}, or {@link #connections} change. */
    public static final int NET_ID_RESOLVE = ID_ALLOC.allocId("RESOLVE");

    /** Sent when a single statement changed. */
    public static final int NET_ID_CHANGE = ID_ALLOC.allocId("STATEMENT_CHANGE");

    /** Sent when {@link #isOn} is true. */
    public static final int NET_ID_GLOWING = ID_ALLOC.allocId("GLOWING");

    /** Sent when {@link #isOn} is false. */
    public static final int NET_ID_DARK = ID_ALLOC.allocId("DARK");

    /* Ideally we wouldn't use a pluggable, but we would use a more generic way of looking at a gate -- perhaps one
     * that's embedded in a robot, or in a minecart. */
    @Deprecated
    public final PluggableGate pluggable;
    public final GateVariant variant;
    public final StatementPair[] statements;

    public final List<StatementSlot> activeActions = new ArrayList<>();

    public java.util.function.Consumer<buildcraft.lib.net.IPayloadWriter> guiMessageOverride;

    /** Used to determine if gate logic should go across several trigger/action pairs. */
    public final boolean[] connections;

    /** Used at the client to display if an action is activated (or would be activated if its not null), or a trigger is
     * currently triggering. */
    public final boolean[] triggerOn, actionOn;

    public int redstoneOutput, redstoneOutputSide;

    private final EnumSet<DyeColor> wireBroadcasts;

    /** Used on the client to determine if this gate should glow or not. */
    public boolean isOn;

    public GateLogic(PluggableGate pluggable, GateVariant variant) {
        this.pluggable = pluggable;
        this.variant = variant;
        statements = new StatementPair[variant.numSlots];
        for (int s = 0; s < variant.numSlots; s++) {
            statements[s] = new StatementPair(s);
        }

        connections = new boolean[variant.numSlots - 1];
        triggerOn = new boolean[variant.numSlots];
        actionOn = new boolean[variant.numSlots];

        wireBroadcasts = EnumSet.noneOf(DyeColor.class);
    }

    // Saving + Loading

    public GateLogic(PluggableGate pluggable, CompoundTag nbt) {
        this(pluggable, new GateVariant(nbt.getCompound("variant").orElse(new CompoundTag())));

        readConfigData(nbt);

        if (nbt.contains("wireBroadcasts")) {
            int[] arr = nbt.getIntArray("wireBroadcasts").orElse(new int[0]);
            for (int i : arr) {
                if (i >= 0 && i < DyeColor.values().length) {
                    wireBroadcasts.add(DyeColor.values()[i]);
                }
            }
        }
    }

    public void readConfigData(CompoundTag nbt) {
        short c = nbt.getShort("connections").orElse((short) 0);
        for (int i = 0; i < connections.length; i++) {
            connections[i] = ((c >>> i) & 1) == 1;
        }

        // Read runtime display state (triggerOn, actionOn, isOn)
        if (nbt.contains("triggerOn")) {
            short tOn = nbt.getShort("triggerOn").orElse((short) 0);
            for (int i = 0; i < triggerOn.length; i++) {
                triggerOn[i] = ((tOn >>> i) & 1) == 1;
            }
        }
        if (nbt.contains("actionOn")) {
            short aOn = nbt.getShort("actionOn").orElse((short) 0);
            for (int i = 0; i < actionOn.length; i++) {
                actionOn[i] = ((aOn >>> i) & 1) == 1;
            }
        }
        isOn = nbt.getBoolean("isOn").orElse(false);

        for (int i = 0; i < statements.length; i++) {
            String tName = "trigger[" + i + "]";
            String aName = "action[" + i + "]";
            // Only apply legacy conversion if the tag has the old format (kind at root)
            // and NOT the new FullStatement format (data inside "s" sub-compound)
            if (nbt.contains(tName)) {
                CompoundTag existing = nbt.getCompound(tName).orElse(new CompoundTag());
                if (existing.contains("kind") && !existing.contains("s")) {
                    // Old 1.12.2 format: kind and side at root level → wrap into "s" sub-compound
                    CompoundTag nbt2 = new CompoundTag();
                    CompoundTag sTag = new CompoundTag();
                    sTag.putString("kind", existing.getString("kind").orElse(""));
                    sTag.putByte("side", existing.getByte("side").orElse((byte) 6));
                    nbt2.put("s", sTag);
                    nbt.put(tName, nbt2);
                }
            }
            if (nbt.contains(aName)) {
                CompoundTag existing = nbt.getCompound(aName).orElse(new CompoundTag());
                if (existing.contains("kind") && !existing.contains("s")) {
                    CompoundTag nbt2 = new CompoundTag();
                    CompoundTag sTag = new CompoundTag();
                    sTag.putString("kind", existing.getString("kind").orElse(""));
                    sTag.putByte("side", existing.getByte("side").orElse((byte) 6));
                    nbt2.put("s", sTag);
                    nbt.put(aName, nbt2);
                }
            }

            statements[i].trigger.readFromNbt(nbt.getCompound(tName).orElse(new CompoundTag()));
            statements[i].action.readFromNbt(nbt.getCompound(aName).orElse(new CompoundTag()));
        }
    }

    public CompoundTag writeToNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("variant", variant.writeToNBT());

        short c = 0;
        for (int i = 0; i < connections.length; i++) {
            if (connections[i]) {
                c |= 1 << i;
            }
        }
        nbt.putShort("connections", c);

        for (int s = 0; s < statements.length; s++) {
            if (statements[s].trigger.get() != null) {
                nbt.put("trigger[" + s + "]", statements[s].trigger.writeToNbt());
            }
            if (statements[s].action.get() != null) {
                nbt.put("action[" + s + "]", statements[s].action.writeToNbt());
            }
        }
        int[] arr = new int[wireBroadcasts.size()];
        int idx = 0;
        for (DyeColor color : wireBroadcasts) {
            arr[idx++] = color.ordinal();
        }
        nbt.putIntArray("wireBroadcasts", arr);

        // Write runtime display state so clients get correct visuals on initial sync
        short tOn = 0;
        for (int i = 0; i < triggerOn.length; i++) {
            if (triggerOn[i]) tOn |= 1 << i;
        }
        nbt.putShort("triggerOn", tOn);
        short aOn = 0;
        for (int i = 0; i < actionOn.length; i++) {
            if (actionOn[i]) aOn |= 1 << i;
        }
        nbt.putShort("actionOn", aOn);
        nbt.putBoolean("isOn", isOn);

        return nbt;
    }

    // Networking

    public GateLogic(PluggableGate pluggable, PacketBufferBC buffer) {
        this(pluggable, new GateVariant(buffer));

        readBoolArray(buffer, triggerOn);
        readBoolArray(buffer, actionOn);
        readBoolArray(buffer, connections);
        try {
            for (StatementPair pair : statements) {
                pair.trigger.readFromBuffer(buffer);
                pair.action.readFromBuffer(buffer);
            }
        } catch (IOException io) {
            throw new Error(io);
        }
        boolean on = false;
        for (int i = 0; i < statements.length; i++) {
            boolean b = actionOn[i];
            on |= b && (statements[i].action.get() != null);
        }
        isOn = on;

    }

    public void writeCreationToBuf(PacketBufferBC buffer) {
        variant.writeToBuffer(buffer);

        writeBoolArray(buffer, triggerOn);
        writeBoolArray(buffer, actionOn);
        writeBoolArray(buffer, connections);

        for (StatementPair pair : statements) {
            pair.trigger.writeToBuffer(buffer);
            pair.action.writeToBuffer(buffer);
        }
    }

    public void readPayload(PacketBufferBC buffer, boolean isClientSide) throws IOException {
        int id = buffer.readUnsignedByte();
        if (id == NET_ID_CHANGE) {
            boolean isAction = buffer.readBoolean();
            int slot = buffer.readUnsignedByte();
            if (slot < 0 || slot >= statements.length) {
                throw new InvalidInputDataException(
                    "Slot index out of range! (" + slot + ", must be within " + statements.length + ")");
            }
            StatementPair s = statements[slot];
            (isAction ? s.action : s.trigger).readFromBuffer(buffer);
            return;
        }
        if (isClientSide) {
            if (id == NET_ID_RESOLVE) {
                readBoolArray(buffer, triggerOn);
                readBoolArray(buffer, actionOn);
                readBoolArray(buffer, connections);
            } else if (id == NET_ID_GLOWING) {
                isOn = true;
                getPipeHolder().scheduleRenderUpdate();
            } else if (id == NET_ID_DARK) {
                isOn = false;
                getPipeHolder().scheduleRenderUpdate();
            } else {
                BCLog.logger.warn("Unknown ID " + ID_ALLOC.getNameFor(id));
            }
        } else {
            BCLog.logger.warn("Unknown server ID " + ID_ALLOC.getNameFor(id));
        }
    }

    public void sendStatementUpdate(boolean isAction, int slot) {
        buildcraft.lib.net.IPayloadWriter writer = (buffer) -> {
            PacketBufferBC buf = PacketBufferBC.asPacketBufferBc(buffer);
            buf.writeByte(NET_ID_CHANGE);
            buf.writeBoolean(isAction);
            buf.writeByte(slot);
            StatementPair s = statements[slot];
            (isAction ? s.action : s.trigger).writeToBuffer(buf);
        };
        
        if (guiMessageOverride != null) {
            guiMessageOverride.accept(writer);
        } else {
            pluggable.sendGuiMessage(writer);
        }
    }

    public void sendResolveData() {
        pluggable.sendGuiMessage((buffer) -> {
            PacketBufferBC buf = PacketBufferBC.asPacketBufferBc(buffer);
            buf.writeByte(NET_ID_RESOLVE);
            writeBoolArray(buf, triggerOn);
            writeBoolArray(buf, actionOn);
            writeBoolArray(buf, connections);
        });
    }

    private static void readBoolArray(PacketBufferBC buf, boolean[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = buf.readBoolean();
        }
    }

    private static void writeBoolArray(PacketBufferBC buf, boolean[] arr) {
        for (int i = 0; i < arr.length; i++) {
            buf.writeBoolean(arr[i]);
        }
    }

    public void sendIsOn() {
        pluggable.sendMessage(buffer -> {
            buffer.writeByte(isOn ? NET_ID_GLOWING : NET_ID_DARK);
        });
    }

    // IGate

    @Override
    public Direction getSide() {
        return pluggable.side;
    }

    @Override
    public BlockEntity getTile() {
        return getPipeHolder().getPipeTile();
    }

    @Override
    public BlockEntity getNeighbourTile(Direction side) {
        return getPipeHolder().getNeighbourTile(side);
    }

    @Override
    public IPipeHolder getPipeHolder() {
        return pluggable.holder;
    }

    @Override
    public List<IStatement> getTriggers() {
        List<IStatement> list = new ArrayList<>(statements.length);
        for (StatementPair pair : statements) {
            TriggerWrapper e = pair.trigger.get();
            list.add(e == null ? e : e.delegate);
        }
        return list;
    }

    @Override
    public List<IStatement> getActions() {
        List<IStatement> list = new ArrayList<>(statements.length);
        for (StatementPair pair : statements) {
            ActionWrapper e = pair.action.get();
            list.add(e == null ? e : e.delegate);
        }
        return list;
    }

    @Override
    public List<StatementSlot> getActiveActions() {
        return activeActions;
    }

    @Override
    public List<IStatementParameter> getTriggerParameters(int slot) {
        return Arrays.asList(statements[slot].trigger.getParameters());
    }

    @Override
    public List<IStatementParameter> getActionParameters(int slot) {
        return Arrays.asList(statements[slot].action.getParameters());
    }

    @Override
    public int getRedstoneInput(Direction side) {
        return getPipeHolder().getRedstoneInput(side);
    }

    @Override
    public boolean setRedstoneOutput(Direction side, int value) {
        return getPipeHolder().setRedstoneOutput(side, value);
    }

    // Wire related

    @Override
    public boolean isEmitting(DyeColor colour) {
        BlockEntity tile = getPipeHolder().getPipeTile();
        if (tile.isRemoved()) {
            return false;
        }
        return wireBroadcasts.contains(colour);
    }

    @Override
    public void emitWire(DyeColor colour) {
        wireBroadcasts.add(colour);
    }

    // Internal Logic

    /** @return True if the gate GUI should be split into 2 separate columns. Needed on the server for the values of
     *         {@link #connections} */
    public boolean isSplitInTwo() {
        return variant.numSlots > 4;
    }

    public void resolveActions() {
        int groupCount = 0;
        int groupActive = 0;

        boolean prevIsOn = isOn;
        isOn = false;
        boolean[] prevTriggers = Arrays.copyOf(triggerOn, triggerOn.length);
        boolean[] prevActions = Arrays.copyOf(actionOn, actionOn.length);

        Arrays.fill(triggerOn, false);
        Arrays.fill(actionOn, false);

        activeActions.clear();

        EnumSet<DyeColor> previousBroadcasts = EnumSet.copyOf(wireBroadcasts);
        wireBroadcasts.clear();

        for (int triggerIndex = 0; triggerIndex < statements.length; triggerIndex++) {
            StatementPair pair = statements[triggerIndex];
            TriggerWrapper trigger = pair.trigger.get();
            groupCount++;
            if (trigger != null) {
                IStatementParameter[] params = new IStatementParameter[pair.trigger.getParamCount()];
                for (int p = 0; p < pair.trigger.getParamCount(); p++) {
                    params[p] = pair.trigger.getParamRef(p).get();
                }
                if (trigger.isTriggerActive(this, params)) {
                    groupActive++;
                    triggerOn[triggerIndex] = true;
                }
            }
            if (connections.length == triggerIndex || !connections[triggerIndex]) {
                boolean allActionsActive;
                if (variant.logic == EnumGateLogic.AND) {
                    allActionsActive = groupActive == groupCount;
                } else {
                    allActionsActive = groupActive > 0;
                }
                for (int i = groupCount - 1; i >= 0; i--) {
                    int actionIndex = triggerIndex - i;
                    StatementPair fullAction = statements[actionIndex];

                    // TODO: add merging / overriding functionality for actions
                    // such that
                    // - (face direction: east)
                    // - (face direction: west)
                    // can be merged (in a single tick) to just
                    // - (face direction: west)
                    // As there's no point in facing both east AND west at the same time
                    // Currently this just faces the pipe east, then west
                    // however it would be *really* useful to optimise that east face set out
                    // in addition we want feedback in the GUI for:
                    // - triggers are on/off
                    // - current action state (for stateful actions)
                    // - and if an action is being overriden (like in the example above)
                    // We might need to expand GUI elements and statements a *lot* for this to work though.
                    // (specifically adding full json-based statement icons and
                    // and full GUI hovers for action + trigger states.)

                    ActionWrapper action = fullAction.action.get();
                    actionOn[actionIndex] = allActionsActive;
                    if (action != null) {
                        if (allActionsActive) {
                            isOn = true;
                            StatementSlot slot = new StatementSlot();
                            slot.statement = action.delegate;
                            slot.parameters = fullAction.action.getParameters().clone();
                            slot.part = action.sourcePart;
                            activeActions.add(slot);
                            action.actionActivate(this, slot.parameters);
                            PipeEvent evt = new PipeEventActionActivate(getPipeHolder(), action.getDelegate(),
                                slot.parameters, action.sourcePart);
                            getPipeHolder().fireEvent(evt);
                        } else {
                            action.actionDeactivated(this, fullAction.action.getParameters());
                        }
                    }
                }
                groupActive = 0;
                groupCount = 0;
            }
        }

        if (!previousBroadcasts.equals(wireBroadcasts)) {
            IWireManager wires = getPipeHolder().getWireManager();
            EnumSet<DyeColor> turnedOff = EnumSet.copyOf(previousBroadcasts);
            turnedOff.removeAll(wireBroadcasts);
            // FIXME: add call to "wires.stopEmittingColour(turnedOff)"

            EnumSet<DyeColor> turnedOn = EnumSet.copyOf(wireBroadcasts);
            turnedOn.removeAll(previousBroadcasts);
            // FIXME: add call to "wires.emittingColour(turnedOff)"

            if (BCModules.TRANSPORT.isLoaded() && !getPipeHolder().getPipeWorld().isClientSide()) {
                try {
                    Class<?> clazz = Class.forName("buildcraft.transport.wire.SavedDataWireSystems");
                    java.lang.reflect.Method getMethod = clazz.getMethod("get", net.minecraft.world.level.Level.class);
                    Object wireSystems = getMethod.invoke(null, getPipeHolder().getPipeWorld());
                    java.lang.reflect.Field field = clazz.getField("gatesChanged");
                    field.setBoolean(wireSystems, true);
                } catch (Exception e) {
                    // Transport module wire systems not available
                }
            }
        }

        if (isOn != prevIsOn) {
            sendIsOn();
        }

        if (!Arrays.equals(prevTriggers, triggerOn) || !Arrays.equals(prevActions, actionOn)) {
            sendResolveData();
        }
    }

    public void onTick() {
        if (getPipeHolder().getPipeWorld().isClientSide()) {
            return;
        }
        resolveActions();
    }

    public SortedSet<TriggerWrapper> getAllValidTriggers() {
        SortedSet<TriggerWrapper> set = new TreeSet<>();
        for (ITriggerInternal trigger : StatementManager.getInternalTriggers(this)) {
            if (isValidTrigger(trigger)) {
                set.add(new TriggerWrapperInternal(trigger));
            }
        }
        for (Direction face : Direction.values()) {
            for (ITriggerInternalSided trigger : StatementManager.getInternalSidedTriggers(this, face)) {
                if (isValidTrigger(trigger)) {
                    set.add(new TriggerWrapperInternalSided(trigger, face));
                }
            }
            BlockEntity neighbour = getNeighbourTile(face);
            if (neighbour != null) {
                for (ITriggerExternal trigger : StatementManager.getExternalTriggers(face, neighbour)) {
                    if (isValidTrigger(trigger)) {
                        set.add(new TriggerWrapperExternal(trigger, face));
                    }
                }
            }
        }
        return set;
    }

    public SortedSet<ActionWrapper> getAllValidActions() {
        SortedSet<ActionWrapper> set = new TreeSet<>();
        for (IActionInternal trigger : StatementManager.getInternalActions(this)) {
            if (isValidAction(trigger)) {
                set.add(new ActionWrapperInternal(trigger));
            }
        }
        for (Direction face : Direction.values()) {
            for (IActionInternalSided trigger : StatementManager.getInternalSidedActions(this, face)) {
                if (isValidAction(trigger)) {
                    set.add(new ActionWrapperInternalSided(trigger, face));
                }
            }
            BlockEntity neighbour = getNeighbourTile(face);
            if (neighbour != null) {
                for (IActionExternal trigger : StatementManager.getExternalActions(face, neighbour)) {
                    if (isValidAction(trigger)) {
                        set.add(new ActionWrapperExternal(trigger, face));
                    }
                }
            }
        }
        return set;
    }

    public boolean isValidTrigger(IStatement statement) {
        return statement != null && statement.minParameters() <= variant.numTriggerArgs;
    }

    public boolean isValidAction(IStatement statement) {
        return statement != null && statement.minParameters() <= variant.numActionArgs;
    }

    public class StatementPair {
        public final FullStatement<TriggerWrapper> trigger;
        public final FullStatement<ActionWrapper> action;

        public StatementPair(int index) {
            IStatementChangeListener tChange = (s, i) -> {
                sendStatementUpdate(false, index);
            };
            IStatementChangeListener aChange = (s, i) -> {
                sendStatementUpdate(true, index);
            };
            trigger = new FullStatement<>(TriggerType.INSTANCE, variant.numTriggerArgs, tChange);
            action = new FullStatement<>(ActionType.INSTANCE, variant.numActionArgs, aChange);
        }
    }
}
