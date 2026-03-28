package buildcraft.energy;

import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

import buildcraft.api.core.BCLog;
import buildcraft.energy.generation.OilGenerator;

/**
 * Hooks oil generation into NeoForge's chunk lifecycle.
 * Uses ChunkEvent.Load with persistent SavedData to ensure each chunk is generated exactly once.
 */
public class BCEnergyWorldGen {

    public static void init() {
        if (!BCEnergyConfig.enableOilGeneration.get()) {
            BCLog.logger.info("[energy.oilgen] Oil generation is disabled by config.");
            return;
        }
        NeoForge.EVENT_BUS.register(BCEnergyWorldGen.class);
        BCLog.logger.info("[energy.oilgen] Registered oil world generation on chunk load.");
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkPos chunkPos = event.getChunk().getPos();

        // Check if we've already generated oil for this chunk
        OilGenSavedData data = OilGenSavedData.getOrCreate(serverLevel);
        if (data.hasGenerated(chunkPos)) {
            return;
        }

        // Mark as generated BEFORE generating to prevent re-entrancy
        data.markGenerated(chunkPos);

        // Generate oil structures that overlap with this chunk
        OilGenerator.generateForChunk(serverLevel, chunkPos.x(), chunkPos.z());
    }

    /**
     * SavedData that tracks which chunks have had oil generation applied.
     * Stored per-dimension in the world save.
     */
    public static class OilGenSavedData extends SavedData {
        private static final String DATA_NAME = "buildcraft_oil_gen";

        private final Set<Long> generatedChunks;

        public OilGenSavedData() {
            this.generatedChunks = new HashSet<>();
        }

        private OilGenSavedData(java.util.List<Long> chunks) {
            this.generatedChunks = new HashSet<>(chunks);
        }

        private static final Codec<OilGenSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.listOf().optionalFieldOf("chunks", java.util.List.of())
                        .forGetter(d -> new java.util.ArrayList<>(d.generatedChunks))
        ).apply(instance, OilGenSavedData::new));

        public static final SavedDataType<OilGenSavedData> TYPE = new SavedDataType<>(
                Identifier.withDefaultNamespace(DATA_NAME),
                OilGenSavedData::new,
                CODEC,
                net.minecraft.util.datafix.DataFixTypes.LEVEL
        );

        public boolean hasGenerated(ChunkPos pos) {
            return generatedChunks.contains(pos.pack());
        }

        public void markGenerated(ChunkPos pos) {
            generatedChunks.add(pos.pack());
            setDirty();
        }

        public static OilGenSavedData getOrCreate(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(TYPE);
        }
    }
}
