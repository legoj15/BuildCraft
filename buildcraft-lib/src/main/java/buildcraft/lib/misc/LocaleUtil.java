package buildcraft.lib.misc;

/** Localization utility. Currently a passthrough stub — returns the key as-is.
 *  Will be wired to Minecraft's I18n when the client layer is ported. */
public class LocaleUtil {
    public static String localize(String key) {
        return key;
    }
}
