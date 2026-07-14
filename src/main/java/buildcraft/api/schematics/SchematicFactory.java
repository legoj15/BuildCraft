package buildcraft.api.schematics;

import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

/**
 * Generic factory holder shared by {@link SchematicBlockFactory} and {@link SchematicEntityFactory}.
 * The two former twins differed only in their context and schematic types, so both are now thin
 * subclasses binding {@code C} (the scan context) and {@code S} (the schematic type). All state and
 * the priority/name ordering live here.
 *
 * @param <C> the scan-context type tested by {@link #predicate}.
 * @param <S> the schematic type produced by {@link #supplier}.
 */
public class SchematicFactory<C, S extends ISchematic<C>> implements Comparable<SchematicFactory<C, ?>> {
    @Nonnull
    public final Object name;
    public final int priority;
    @Nonnull
    public final Predicate<C> predicate;
    @Nonnull
    public final Supplier<S> supplier;
    @Nonnull
    public final Class<S> clazz;

    @SuppressWarnings("unchecked")
    public SchematicFactory(@Nonnull Object name,
                            int priority,
                            @Nonnull Predicate<C> predicate,
                            @Nonnull Supplier<S> supplier) {
        this.name = name;
        this.priority = priority;
        this.predicate = predicate;
        this.supplier = supplier;
        clazz = (Class<S>) supplier.get().getClass();
    }

    @Override
    public int compareTo(@Nonnull SchematicFactory<C, ?> o) {
        return priority != o.priority
                ? Integer.compare(priority, o.priority)
                : name.toString().compareTo(o.name.toString());
    }
}
