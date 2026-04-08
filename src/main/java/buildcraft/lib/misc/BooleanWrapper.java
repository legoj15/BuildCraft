package buildcraft.lib.misc;

/** Temporary stub replacing buildcraft.api.expression.IVariableNodeBoolean until the expression API is ported. */
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
