package buildcraft.lib.client.guide.entry;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import buildcraft.lib.gui.ISimpleDrawable;

public class PageValue<T> {

    public final PageValueType<T> type;
    public final String title;
    public final T value;

    public PageValue(PageValueType<T> type, T value) {
        this.type = type;
        this.title = type.getTitle(value);
        this.value = value;
    }

    public static String getTitle(JsonObject json) {
        // JsonUtil.getTextComponent not yet available — use raw title field
        if (json.has("title")) {
            return json.get("title").getAsString();
        }
        return "untitled";
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
