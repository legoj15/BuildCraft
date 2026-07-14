package buildcraft.api.schematics;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import net.minecraft.resources.Identifier;

import buildcraft.api.core.BuildCraftAPI;

/**
 * Generic priority-ordered registry of {@link SchematicFactory} instances, shared by
 * {@link SchematicBlockFactoryRegistry} and {@link SchematicEntityFactoryRegistry}. Those two hold a
 * private static instance of this class and delegate their (unchanged, addon-facing) static methods
 * to it — the register/matcher overloads that genuinely differ (block vs entity) stay on the facades.
 *
 * @param <C> the scan-context type.
 * @param <F> the concrete factory subtype stored (e.g. {@code SchematicBlockFactory<?>}), so
 *            {@link #getFactories()} returns the exact type the facade's public contract promises.
 */
public class SchematicFactoryRegistry<C, F extends SchematicFactory<C, ?>> {
    private final Set<F> factories = new TreeSet<>();

    public void register(F factory) {
        factories.add(factory);
    }

    public List<F> getFactories() {
        return ImmutableList.copyOf(factories);
    }

    @Nonnull
    public F getFactoryByInstance(Object instance) {
        return factories.stream()
            .filter(factory -> factory.clazz == instance.getClass())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Didn't find a factory for " + instance.getClass()));
    }

    /**
     * Look up a factory by its registered id. Tolerates either an {@link Identifier} or a
     * {@code String} with "namespace:path"; strings are parsed through
     * {@link BuildCraftAPI#nameToResourceLocation(String)} so the comparison below is always
     * {@code Identifier.equals(Identifier)}. This defends against the old bug where factories
     * were keyed by {@code String} and the caller passed {@code Identifier} — the two types are
     * never equal, which silently broke every deserialization path.
     */
    @Nullable
    public F getFactoryByName(Object name) {
        Identifier id = name instanceof Identifier i ? i
                : name instanceof String s ? BuildCraftAPI.nameToResourceLocation(s)
                : null;
        if (id == null) return null;
        return factories.stream()
            .filter(factory -> id.equals(factory.name))
            .findFirst()
            .orElse(null);
    }
}
