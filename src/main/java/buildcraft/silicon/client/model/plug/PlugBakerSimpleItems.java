package buildcraft.silicon.client.model.plug;

import java.util.List;

//? if >=26.1 {
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import net.minecraft.client.renderer.block.model.BakedQuad;*/
//?}

import buildcraft.api.transport.pluggable.IPluggableStaticBaker;

import buildcraft.lib.client.model.ModelHolderStatic;
import buildcraft.lib.client.model.plug.PlugBakerSimple;

import buildcraft.silicon.client.model.key.KeyPlugSimple;

/** Static geometry for the timer, light sensor and pulsar base. Each shape is a resource-pack-overridable
 * {@code models/plugs/*.json} (ported from 1.12.2), rotated to its side by {@link PlugBakerSimple}. The pulsar's
 * moving part is drawn separately by {@code PlugPulsarRenderer}. */
public enum PlugBakerSimpleItems implements IPluggableStaticBaker<KeyPlugSimple> {
    INSTANCE;

    // Holders must exist before the first resource reload to be picked up by ModelHolderRegistry: see init()
    private static final ModelHolderStatic TIMER = new ModelHolderStatic("buildcraftunofficial:models/plugs/timer.json");
    private static final ModelHolderStatic LIGHT_SENSOR = new ModelHolderStatic("buildcraftunofficial:models/plugs/light_sensor.json");
    private static final ModelHolderStatic PULSAR = new ModelHolderStatic("buildcraftunofficial:models/plugs/pulsar_static.json");

    private static final PlugBakerSimple<KeyPlugSimple> BAKER_TIMER = new PlugBakerSimple<>(TIMER::getCutoutQuads);
    private static final PlugBakerSimple<KeyPlugSimple> BAKER_LIGHT_SENSOR = new PlugBakerSimple<>(LIGHT_SENSOR::getCutoutQuads);
    private static final PlugBakerSimple<KeyPlugSimple> BAKER_PULSAR = new PlugBakerSimple<>(PULSAR::getCutoutQuads);

    /** Forces class initialization so the model holders above register before the first reload. */
    public static void init() {
    }

    @Override
    public List<BakedQuad> bake(KeyPlugSimple key) {
        return switch (key.identifier) {
            case "timer" -> BAKER_TIMER.bake(key);
            case "daylight_sensor" -> BAKER_LIGHT_SENSOR.bake(key);
            case "pulsar" -> BAKER_PULSAR.bake(key);
            default -> List.of();
        };
    }
}
