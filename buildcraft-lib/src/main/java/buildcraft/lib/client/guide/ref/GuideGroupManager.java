package buildcraft.lib.client.guide.ref;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.statements.IStatement;

import buildcraft.lib.client.guide.entry.ItemStackValueFilter;
import buildcraft.lib.client.guide.entry.PageEntryItemStack;
import buildcraft.lib.client.guide.entry.PageEntryStatement;
import buildcraft.lib.client.guide.entry.PageValue;
import buildcraft.lib.client.guide.entry.PageValueType;

public class GuideGroupManager {
    public static final List<PageValueType<?>> knownTypes = new ArrayList<>();
    public static final Map<ResourceLocation, GuideGroupSet> sets = new HashMap<>();

    private static final Map<Class<?>, PageValueType<?>> knownClasses = new WeakHashMap<>();
    private static final Map<Class<?>, Function<Object, PageValue<?>>> transformers = new WeakHashMap<>();

    static {
        addValidClass(ItemStackValueFilter.class, PageEntryItemStack.INSTANCE);
        addValidClass(IStatement.class, PageEntryStatement.INSTANCE);
        addTransformer(ItemStack.class, ItemStackValueFilter.class, ItemStackValueFilter::new);
        addTransformer(Item.class, ItemStack.class, ItemStack::new);
        addTransformer(Block.class, ItemStack.class, ItemStack::new);

        // temp() — hardcoded group entries deferred (BCBlocks/BCItems not populated yet)
    }

    public static <F, T> void addTransformer(Class<F> fromClass, Class<T> toClass, Function<F, T> transform) {
        if (isValidClass(fromClass)) {
            throw new IllegalArgumentException("You cannot register a transformer from an already-registered class!");
        }
        PageValueType<?> destType = getEntryType(toClass);
        if (destType == null) {
            Function<Object, PageValue<?>> destTransform = getTransform(toClass);
            if (destTransform != null) {
                Function<Object, PageValue<?>> realTransform = o -> {
                    F from = fromClass.cast(o);
                    T to = transform.apply(from);
                    return destTransform.apply(to);
                };
                transformers.put(fromClass, realTransform);
                return;
            }
            throw new IllegalArgumentException("You cannot register a transformer to an unregistered class!");
        }
        Function<Object, PageValue<?>> realTransform = o -> {
            F from = fromClass.cast(o);
            T to = transform.apply(from);
            return destType.wrap(to);
        };
        transformers.put(fromClass, realTransform);
    }

    public static <T> void addValidClass(Class<T> clazz, PageValueType<T> type) {
        if (clazz.isArray()) {
            throw new IllegalArgumentException("Arrays are never valid!");
        }
        knownClasses.put(clazz, type);
        knownTypes.add(type);
    }

    static boolean isValidObject(Object value) {
        if (value == null) return false;
        return isValidClass(value.getClass());
    }

    public static PageValue<?> toPageValue(Object value) {
        if (value == null) return null;
        if (value instanceof PageValue) return (PageValue<?>) value;
        PageValueType<?> entryType = getEntryType(value.getClass());
        if (entryType != null) return entryType.wrap(value);
        Function<Object, PageValue<?>> transform = getTransform(value.getClass());
        if (transform != null) return transform.apply(value);
        throw new IllegalArgumentException("Unknown " + value.getClass()
            + " - is this a programming mistake, or have you forgotten to register the class as valid?");
    }

    private static boolean isValidClass(Class<?> clazz) {
        return getEntryType(clazz) != null;
    }

    @Nullable
    private static PageValueType<?> getEntryType(Class<?> clazz) {
        if (knownClasses.containsKey(clazz)) return knownClasses.get(clazz);
        PageValueType<?> type = null;
        if (!clazz.isArray()) {
            search: {
                Class<?> superClazz = clazz.getSuperclass();
                if (superClazz != null) {
                    type = getEntryType(superClazz);
                    if (type != null) break search;
                }
                for (Class<?> cls : clazz.getInterfaces()) {
                    type = getEntryType(cls);
                    if (type != null) break search;
                }
            }
            knownClasses.put(clazz, type);
        }
        return type;
    }

    private static Function<Object, PageValue<?>> getTransform(Class<? extends Object> clazz) {
        Function<Object, PageValue<?>> func = transformers.get(clazz);
        if (func != null) return func;
        if (!clazz.isArray()) {
            search: {
                Class<?> superClazz = clazz.getSuperclass();
                if (superClazz != null) {
                    func = getTransform(superClazz);
                    if (func != null) break search;
                }
                for (Class<?> cls : clazz.getInterfaces()) {
                    func = getTransform(cls);
                    if (func != null) break search;
                }
            }
            transformers.put(clazz, func);
        }
        return func;
    }

    @Nullable
    public static GuideGroupSet get(ResourceLocation group) {
        return sets.get(group);
    }

    @Nullable
    public static GuideGroupSet get(String domain, String group) {
        return get(ResourceLocation.fromNamespaceAndPath(domain, group));
    }

    public static GuideGroupSet getOrCreate(String domain, String group) {
        return sets.computeIfAbsent(ResourceLocation.fromNamespaceAndPath(domain, group), GuideGroupSet::new);
    }

    public static GuideGroupSet addEntry(String domain, String group, Object value) {
        return getOrCreate(domain, group).addSingle(value);
    }

    public static GuideGroupSet addEntries(String domain, String group, Object... values) {
        return getOrCreate(domain, group).addArray(values);
    }

    public static GuideGroupSet addEntries(String domain, String group, Collection<Object> values) {
        return getOrCreate(domain, group).addCollection(values);
    }

    public static GuideGroupSet addKey(String domain, String group, Object value) {
        return getOrCreate(domain, group).addKey(value);
    }

    public static GuideGroupSet addKeys(String domain, String group, Object... values) {
        return getOrCreate(domain, group).addKeyArray(values);
    }

    public static GuideGroupSet addKeys(String domain, String group, Collection<Object> values) {
        return getOrCreate(domain, group).addKeyCollection(values);
    }
}
