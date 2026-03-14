package buildcraft.lib.config;

/** Stub for BuildCraft config options. In 1.12.2 this read from ForgeConfig;
 *  for now it returns sensible defaults. */
public class DetailedConfigOption {
    private final String key;
    private final String defaultValue;

    public DetailedConfigOption(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public float getAsFloat() {
        try {
            return Float.parseFloat(defaultValue);
        } catch (NumberFormatException e) {
            return 0.725f;
        }
    }

    public int getAsInt() {
        try {
            return Integer.parseInt(defaultValue);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String get() {
        return defaultValue;
    }
}
