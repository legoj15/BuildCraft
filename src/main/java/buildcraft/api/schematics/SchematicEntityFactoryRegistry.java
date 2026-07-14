package buildcraft.api.schematics;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import buildcraft.api.core.BuildCraftAPI;

public class SchematicEntityFactoryRegistry {
    private static final SchematicFactoryRegistry<SchematicEntityContext, SchematicEntityFactory<?>> REGISTRY =
        new SchematicFactoryRegistry<>();

    public static <S extends ISchematicEntity> void registerFactory(String name,
                                                                    int priority,
                                                                    Predicate<SchematicEntityContext> predicate,
                                                                    Supplier<S> supplier) {
        REGISTRY.register(new SchematicEntityFactory<>(
            BuildCraftAPI.nameToResourceLocation(name),
            priority,
            predicate,
            supplier
        ));
    }

    public static <S extends ISchematicEntity> void registerFactory(String name,
                                                                    int priority,
                                                                    List<Object> entities,
                                                                    Supplier<S> supplier) {
        registerFactory(
            name,
            priority,
            context -> entities.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(context.entity.getType())),
            supplier
        );
    }

    public static List<SchematicEntityFactory<?>> getFactories() {
        return REGISTRY.getFactories();
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    public static <S extends ISchematicEntity> SchematicEntityFactory<S> getFactoryByInstance(S instance) {
        return (SchematicEntityFactory<S>) REGISTRY.getFactoryByInstance(instance);
    }

    @Nullable
    public static SchematicEntityFactory<?> getFactoryByName(Object name) {
        return REGISTRY.getFactoryByName(name);
    }
}
