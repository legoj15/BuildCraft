package buildcraft.api.schematics;

import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

/**
 * Thin block-scoped view over {@link SchematicFactory}. Kept as a named subclass (rather than a bare
 * {@code SchematicFactory<SchematicBlockContext, S>}) so addon-facing signatures and the
 * {@code getFactories() : List<SchematicBlockFactory<?>>} contract stay unchanged.
 */
public class SchematicBlockFactory<S extends ISchematicBlock> extends SchematicFactory<SchematicBlockContext, S> {
    public SchematicBlockFactory(@Nonnull Object name,
                                 int priority,
                                 @Nonnull Predicate<SchematicBlockContext> predicate,
                                 @Nonnull Supplier<S> supplier) {
        super(name, priority, predicate, supplier);
    }
}
