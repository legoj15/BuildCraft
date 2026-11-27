package buildcraft.lib.statement;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.UnaryOperator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;

import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementParameter;

import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.IReference;
import buildcraft.lib.net.PacketBufferBC;

/** Util class for holding, saving, loading and networking {@link StatementWrapper} and its
 * {@link IStatementParameter}'s. */
public class FullStatement<S extends IStatement> implements IReference<S> {
    public final StatementType<S> type;
    public final int maxParams;
    public boolean canInteract = true;
    private final IStatementChangeListener listener;
    private final IStatementParameter[] params;
    private final ParamRef[] paramRefs;
    private S statement;

    public FullStatement(StatementType<S> type, int maxParams, IStatementChangeListener listener) {
        this.type = type;
        this.statement = type.defaultStatement;
        this.listener = listener;
        this.maxParams = maxParams;
        this.params = new IStatementParameter[maxParams];
        this.paramRefs = new FullStatement.ParamRef[maxParams];
        for (int i = 0; i < maxParams; i++) {
            paramRefs[i] = new ParamRef(this, i);
        }
    }

    // NBT

    public void readFromNbt(CompoundTag nbt) {
        statement = type.readFromNbt(NBTUtilBC.getCompound(nbt, "s"));
        if (statement == null) {
            Arrays.fill(params, null);
        } else {
            for (int p = 0; p < params.length; p++) {
                CompoundTag pNbt = NBTUtilBC.getCompound(nbt, Integer.toString(p));
                params[p] = StatementTypeParam.INSTANCE.readFromNbt(pNbt);
            }
            clearUnusedParams();
        }
    }

    /** Slots past the statement's last parameter are drawn blocked, so nothing may stay in them for the statement
     *  to read: an older save or a client packet can still carry one left there by the previous statement. */
    private void clearUnusedParams() {
        for (int p = statement.maxParameters(); p < params.length; p++) {
            params[p] = null;
        }
    }

    public CompoundTag writeToNbt() {
        CompoundTag nbt = new CompoundTag();
        if (statement != null) {
            nbt.put("s", type.writeToNbt(statement));
            for (int p = 0; p < params.length; p++) {
                IStatementParameter param = params[p];
                if (param != null) {
                    nbt.put(Integer.toString(p), StatementTypeParam.INSTANCE.writeToNbt(param));
                }
            }
        }
        return nbt;
    }

    // Networking

    public void readFromBuffer(PacketBufferBC buffer) throws IOException {
        if (buffer.readBoolean()) {
            statement = type.readFromBuffer(buffer);
            for (int p = 0; p < params.length; p++) {
                params[p] = StatementTypeParam.INSTANCE.readFromBuffer(buffer);
            }
            if (statement != null) {
                clearUnusedParams();
            }
        } else {
            statement = type.defaultStatement;
            Arrays.fill(params, null);
        }
    }

    public void writeToBuffer(PacketBufferBC buffer) {
        if (statement == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            type.writeToBuffer(buffer, statement);
            for (int p = 0; p < params.length; p++) {
                IStatementParameter param = params[p];
                StatementTypeParam.INSTANCE.writeToBuffer(buffer, param);
            }
        }
    }

    // IReference

    @Override
    public S get() {
        return statement;
    }

    @Override
    public void set(S to) {
        statement = to;
        if (statement == null) {
            Arrays.fill(params, null);
            return;
        }
        for (int i = 0; i < params.length; i++) {
            if (i >= statement.maxParameters()) {
                params[i] = null;
            } else {
                params[i] = statement.createParameter(params[i], i);
            }
        }
    }

    @Override
    public boolean canSet(S value) {
        if (value == null) {
            return true;
        }
        return value.minParameters() <= params.length;
    }

    @Override
    public S convertToType(Object value) {
        S val = IReference.super.convertToType(value);
        if (value != null && val == null) {
            return type.convertToType(value);
        }
        return val;
    }

    @Override
    public Class<S> getHeldType() {
        return type.clazz;
    }

    static class ParamRef implements IReference<IStatementParameter> {
        public final IReference<? extends IStatement> statementRef;
        public final IStatementParameter[] array;
        public final int index;

        public ParamRef(FullStatement<?> full, int index) {
            statementRef = full;
            this.array = full.params;
            this.index = index;
        }

        @Override
        public IStatementParameter get() {
            return array[index];
        }

        @Override
        public void set(IStatementParameter to) {
            array[index] = to;
        }

        @Override
        public boolean canSet(IStatementParameter value) {
            IStatement statement = statementRef.get();
            if (statement == null || index >= statement.maxParameters()) {
                return false;
            }
            return statement.createParameter(value, index) == value;
        }

        @Override
        public Class<IStatementParameter> getHeldType() {
            return IStatementParameter.class;
        }
    }

    // Params

    public IReference<IStatementParameter> getParamRef(int i) {
        return paramRefs[i];
    }

    public IStatementParameter get(int index) {
        return getParamRef(index).get();
    }

    public void set(int index, IStatementParameter param) {
        getParamRef(index).set(param);
    }

    public void set(S statement, IStatementParameter[] params) {
        set(statement);
        if (statement == null || params == null) {
            return;
        }
        int count = Math.min(Math.min(getParamCount(), params.length), statement.maxParameters());
        for (int i = 0; i < count; i++) {
            // A gate save loads parameters by kind alone: a missing or wrong-kind one falls back to the default.
            set(i, statement.createParameter(params[i], i));
        }
    }

    public boolean canSet(int index, IStatementParameter param) {
        return getParamRef(index).canSet(param);
    }

    public int getParamCount() {
        return params.length;
    }

    public IStatementParameter[] getParameters() {
        return params;
    }

    // Rotation

    /** How many clockwise quarter turns {@code rotation} is: {@link Rotation#CLOCKWISE_90} one,
     * {@link Rotation#CLOCKWISE_180} two, {@link Rotation#COUNTERCLOCKWISE_90} three and {@link Rotation#NONE} none.
     * One {@link IStatementParameter#rotateLeft()} is one of them. */
    public static int quarterTurns(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
    }

    /** {@link #rotateParameters(CompoundTag, int, int, UnaryOperator)} with every parameter turned on its own by
     * {@link IStatementParameter#rotateLeft()}. */
    public static void rotateParameters(CompoundTag statement, int quarterTurns) {
        rotateParameters(statement, 0, quarterTurns, FullStatement::rotateEach);
    }

    /**
     * Turns, in place, the parameters of {@code statement}, a tag {@link #writeToNbt()} wrote: its numbered keys
     * {@code "0"}, {@code "1"} and so on each hold one parameter tag. The parameters are read into one array (a slot
     * with no key, or one that does not read, holds null; the array is at least {@code slots} long, so a slot the
     * statement has but the tag lacks can still be filled), handed to {@code quarterTurn} once per quarter turn, and
     * each slot that came back as a different parameter is stored again. A parameter that comes back as the same one
     * is left as it is, so a statement whose parameters name no direction is not rewritten. Anything else in the tag
     * is left alone, and a key that is not a one to three digit number is not a parameter.
     *
     * @param quarterTurn one clockwise quarter turn of the whole parameter list: the array it is given is its own copy,
     *            and it returns the turned list in the same slots (null for a slot that holds nothing)
     */
    public static void rotateParameters(CompoundTag statement, int slots, int quarterTurns,
                                        UnaryOperator<IStatementParameter[]> quarterTurn) {
        int length = slots;
        for (String key : NBTUtilBC.keysOf(statement)) {
            if (isParameterKey(key)) {
                length = Math.max(length, Integer.parseInt(key) + 1);
            }
        }
        if (length == 0 || quarterTurns <= 0) {
            return;
        }
        IStatementParameter[] original = new IStatementParameter[length];
        for (String key : NBTUtilBC.keysOf(statement)) {
            CompoundTag tag = isParameterKey(key) ? NBTUtilBC.getCompoundOrNull(statement, key) : null;
            if (tag != null) {
                original[Integer.parseInt(key)] = readParameter(tag);
            }
        }
        // The callee gets a copy, so what it does to the array it is given cannot change what is compared below.
        IStatementParameter[] turned = original.clone();
        for (int i = 0; i < quarterTurns; i++) {
            turned = quarterTurn.apply(turned);
        }
        for (int slot = 0; slot < length && slot < turned.length; slot++) {
            if (turned[slot] != null && turned[slot] != original[slot]) {
                statement.put(Integer.toString(slot), StatementTypeParam.INSTANCE.writeToNbt(turned[slot]));
            }
        }
    }

    /** {@code params} with each parameter turned one clockwise quarter turn by its own
     *  {@link IStatementParameter#rotateLeft()}, in a new array: a slot that holds nothing stays empty. */
    public static IStatementParameter[] rotateEach(IStatementParameter[] params) {
        IStatementParameter[] turned = new IStatementParameter[params.length];
        for (int i = 0; i < params.length; i++) {
            turned[i] = params[i] == null ? null : params[i].rotateLeft();
        }
        return turned;
    }

    /** Whether {@code key} is one of a statement tag's numbered parameter keys ({@code "0"}, {@code "1"}, ...). */
    private static boolean isParameterKey(String key) {
        return key.length() >= 1 && key.length() <= 3 && key.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** The parameter {@code tag} holds, or null when it names none or cannot be read. */
    private static IStatementParameter readParameter(CompoundTag tag) {
        try {
            return StatementTypeParam.INSTANCE.readFromNbt(tag);
        } catch (RuntimeException e) {
            // Blueprint data is not guaranteed well formed: a parameter that does not read is left as it is.
            return null;
        }
    }

    // Gui change listeners

    /** Gui elements should call this after calling {@link #set(IStatement)} or {@link #set(int, IStatementParameter)},
     * with either -1 or the param index respectively. */
    public void postSetFromGui(int paramIndex) {
        if (listener != null) {
            listener.onChange(this, paramIndex);
        }
    }

    @FunctionalInterface
    public interface IStatementChangeListener {
        /** @param statement The statement that changed
         * @param paramIndex The index of the parameter that changed, or -1 if the main {@link IStatement} changed. */
        void onChange(FullStatement<?> statement, int paramIndex);
    }
}
