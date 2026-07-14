package buildcraft.api.schematics;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.level.block.Block;

import buildcraft.api.core.BuildCraftAPI;

public class SchematicBlockFactoryRegistry {
    private static final SchematicFactoryRegistry<SchematicBlockContext, SchematicBlockFactory<?>> REGISTRY =
        new SchematicFactoryRegistry<>();

    public static <S extends ISchematicBlock> void registerFactory(String name,
                                                                   int priority,
                                                                   Predicate<SchematicBlockContext> predicate,
                                                                   Supplier<S> supplier) {
        REGISTRY.register(new SchematicBlockFactory<>(
            BuildCraftAPI.nameToResourceLocation(name),
            priority,
            predicate,
            supplier
        ));
    }

    public static <S extends ISchematicBlock> void registerFactory(String name,
                                                                   int priority,
                                                                   List<Block> blocks,
                                                                   Supplier<S> supplier) {
        registerFactory(
            name,
            priority,
            context -> blocks.contains(context.block),
            supplier
        );
    }

    public static List<SchematicBlockFactory<?>> getFactories() {
        return REGISTRY.getFactories();
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public static <S extends ISchematicBlock> SchematicBlockFactory<S> getFactoryByInstance(S instance) {
        return (SchematicBlockFactory<S>) REGISTRY.getFactoryByInstance(instance);
    }

    @Nullable
    public static SchematicBlockFactory<?> getFactoryByName(Object name) {
        return REGISTRY.getFactoryByName(name);
    }
}
