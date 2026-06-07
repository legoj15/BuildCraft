package buildcraft.builders;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import buildcraft.builders.entity.EntityQuarryRig;

public class BCBuildersEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BCBuilders.MODID);

    public static final Supplier<EntityType<EntityQuarryRig>> QUARRY_RIG = ENTITIES.register(
            "quarry_rig",
            (key) -> EntityType.Builder.<EntityQuarryRig>of(EntityQuarryRig::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    //? if >=1.21.10 {
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, key)));
                    //?} else {
                    /*.build(key.toString()));*/
                    //?}

    public static void init(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
