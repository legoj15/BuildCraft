package buildcraft.energy.generation;

import com.mojang.serialization.Codec;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import buildcraft.core.BCCoreConfig;
import buildcraft.energy.BCEnergyConfig;

/**
 * Worldgen {@link Feature} that places BuildCraft's oil structures (surface pools/tendrils,
 * underground spheres, spouts, bedrock springs) while a chunk is being generated.
 *
 * <p>This replaces the old {@code ChunkEvent.Load} generation path, and doing so retires an entire
 * class of "worldgen deadlock" bugs (GitHub issue #26 and its predecessors): on the live
 * {@code ServerLevel}, any cross-chunk read or write into a not-yet-generated neighbour performs a
 * blocking {@code ServerChunkCache.getChunk} that parks the server thread forever. Inside a
 * {@link net.minecraft.server.level.WorldGenRegion} that failure mode is structurally impossible —
 * the region never force-loads: an out-of-region access throws immediately (a loud, debuggable
 * crash instead of a silent freeze), {@code setBlock} fires no neighbour/shape/{@code onPlace}
 * updates, and writes are clipped to the generating chunk anyway (see
 * {@link OilGenerator#generateForChunk}).
 *
 * <p>Runs at {@code top_layer_modification} — after {@code vegetal_decoration}, so the surface-pool
 * tree clearing sees the chunk's trees. It is attached to <b>every</b> biome by
 * {@link AddOilBiomeModifier}; biome/dimension eligibility, rates, and config gates are evaluated
 * here at place time (they always were runtime config reads — a datapack biome list could not
 * express them).
 *
 * <p>The per-origin-chunk RNG is seed-deterministic and unchanged from the old path, so worlds
 * started before this migration keep identical oil placement — new chunks tile seamlessly against
 * chunks that generated under the {@code ChunkEvent.Load} model.
 */
public class OilFeature extends Feature<NoneFeatureConfiguration> {

    public OilFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        // Live config reads, mirroring the gates the old ChunkEvent.Load registration checked once
        // at init. The feature itself is always registered (registries are unconditional).
        if (!BCCoreConfig.worldGen.get() || !BCEnergyConfig.enableOilGeneration.get()) {
            return false;
        }
        WorldGenLevel level = context.level();
        int chunkX = SectionPos.blockToSectionCoord(context.origin().getX());
        int chunkZ = SectionPos.blockToSectionCoord(context.origin().getZ());
        return OilGenerator.generateForChunk(level, chunkX, chunkZ);
    }
}
