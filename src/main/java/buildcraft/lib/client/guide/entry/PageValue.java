package buildcraft.lib.client.guide.entry;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import buildcraft.lib.gui.ISimpleDrawable;
import buildcraft.lib.misc.LocaleUtil;

public class PageValue<T> {

    public final PageValueType<T> type;
    public final String title;
    public final T value;

    public PageValue(PageValueType<T> type, T value) {
        this(type, value, null);
    }

    public PageValue(PageValueType<T> type, T value, @Nullable String titleOverride) {
        this.type = type;
        this.title = titleOverride != null ? titleOverride : type.getTitle(value);
        this.value = value;
    }

    public static String getTitle(JsonObject json) {
        String override = getTitleOverride(json);
        return override != null ? override : "untitled";
    }

    /** Reads the optional `title` / `title_raw` field from a page-entry JSON, returning
     *  null when neither is present. Lets entry types whose default {@code getTitle}
     *  pulls from elsewhere (e.g. {@link PageEntryItemStack} reading the item's hover
     *  name) accept an explicit override without disturbing existing defaults. */
    @Nullable
    public static String getTitleOverride(JsonObject json) {
        // Mirrors 1.12.2's JsonUtil.getTextComponent(json, "title", "buildcraft.guide.page.").
        // The "title" field is a translation-key suffix; the prefix is prepended and the
        // result is localized. "title_raw" bypasses localization. If no translation exists
        // for the prefixed key, fall back to the raw string (better than showing the key).
        if (json.has("title")) {
            String str = json.get("title").getAsString();
            String prefixed = "buildcraft.guide.page." + str;
            String localized = LocaleUtil.localize(prefixed);
            return prefixed.equals(localized) ? str : localized;
        }
        if (json.has("title_raw")) {
            return json.get("title_raw").getAsString();
        }
        return null;
    }

    /** @param test An unknown object.
     * @return True if it matches {@link #value} */
    public boolean matches(Object test) {
        return type.matches(value, test);
    }

    @Nullable
    public ISimpleDrawable createDrawable() {
        return type.createDrawable(value);
    }

    public Object getBasicValue() {
        return type.getBasicValue(value);
    }

    public List<String> getTooltip() {
        return type.getTooltip(value);
    }

    public PageValue<T> copyToValue() {
        return new PageValue<>(type, value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) {
            return false;
        }
        PageValue<?> other = (PageValue<?>) obj;
        return Objects.equals(value, other.value);
    }
}
