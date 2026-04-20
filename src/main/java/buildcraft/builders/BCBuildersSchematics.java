package buildcraft.builders;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.api.schematics.SchematicBlockFactoryRegistry;
import buildcraft.api.schematics.SchematicEntityFactoryRegistry;

import buildcraft.builders.snapshot.SchematicBlockAir;
import buildcraft.builders.snapshot.SchematicBlockDefault;
import buildcraft.builders.snapshot.SchematicBlockFluid;
import buildcraft.builders.snapshot.SchematicEntityDefault;

public class BCBuildersSchematics {
    public static void preInit() {
        registerSchematicFactory("buildcraftunofficial:air", 0, SchematicBlockAir::predicate, SchematicBlockAir::new);
        registerSchematicFactory("buildcraftunofficial:default", 100, SchematicBlockDefault::predicate, SchematicBlockDefault::new);
        registerSchematicFactory("buildcraftunofficial:fluid", 200, SchematicBlockFluid::predicate, SchematicBlockFluid::new);

        // Removed vine and banner special handling
        SchematicEntityFactoryRegistry.registerFactory("buildcraftunofficial:default", 100, SchematicEntityDefault::predicate,
            SchematicEntityDefault::new);
    }

    private static <S extends ISchematicBlock> void registerSchematicFactory(String name, int priority,
        Predicate<SchematicBlockContext> predicate, Supplier<S> supplier) {
        SchematicBlockFactoryRegistry.registerFactory(name, priority, predicate, supplier);
    }
}
