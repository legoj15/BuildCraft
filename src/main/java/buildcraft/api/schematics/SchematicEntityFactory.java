package buildcraft.api.schematics;

import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

/**
 * Thin entity-scoped view over {@link SchematicFactory}. Kept as a named subclass (rather than a bare
 * {@code SchematicFactory<SchematicEntityContext, S>}) so addon-facing signatures and the
 * {@code getFactories() : List<SchematicEntityFactory<?>>} contract stay unchanged.
 */
public class SchematicEntityFactory<S extends ISchematicEntity> extends SchematicFactory<SchematicEntityContext, S> {
    public SchematicEntityFactory(@Nonnull Object name,
                                  int priority,
                                  @Nonnull Predicate<SchematicEntityContext> predicate,
                                  @Nonnull Supplier<S> supplier) {
        super(name, priority, predicate, supplier);
    }
}
