package buildcraft.energy;

import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import buildcraft.api.core.BCLog;
import buildcraft.core.BCCoreConfig;
import buildcraft.energy.generation.OilGenerator;
import buildcraft.lib.misc.AdvancementUtil;

/**
 * Hooks oil generation into NeoForge's chunk lifecycle.
 * Uses ChunkEvent.Load with persistent SavedData to ensure each chunk is generated exactly once.
 */
public class BCEnergyWorldGen {

    private static final Identifier ADVANCEMENT_FINE_RICHES
        = Identifier.parse("buildcraftunofficial:fine_riches");

    public static void init() {
        if (!BCCoreConfig.worldGen.get() || !BCEnergyConfig.enableOilGeneration.get()) {
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
        OilGenerator.generateForChunk(serverLevel, buildcraft.lib.misc.PositionUtil.chunkX(chunkPos), buildcraft.lib.misc.PositionUtil.chunkZ(chunkPos));
    }

    /** Once-per-second check stride for {@link #onPlayerTick}. The advancement is
     * idempotent so missing a tick costs nothing; a player walking at sprint speed
     * (~5.6 m/s ≈ 0.35 chunks/sec) advances about a third of a chunk per second,
     * so this rate still catches every chunk transition with plenty of margin. */
    private static final int FINE_RICHES_TICK_STRIDE = 20;

    /** Half-extent (in chunks) of the scan around the player for the rolled-oil
     * check in {@link #onPlayerTick}. Sized to cover the maximum LARGE/LAKE
     * tendril radius — both types use {@code tendrilRadius = 25 + rand.nextInt(20)},
     * up to 45 blocks ≈ 2.8 chunks, rounded up. With a smaller radius the player
     * can stand directly in a lake of oil whose origin chunk is too far away to
     * be caught, getting {@code sticky_dipping} but not {@code fine_riches}.
     * MEDIUM tendrils only reach ±1 chunk so this is a safe over-approximation.
     * 49 chunk RNG replays per check (~µs each) at the once-per-second cadence
     * is negligible. */
    private static final int FINE_RICHES_SCAN_RADIUS = 3;

    /**
     * Awards the {@code fine_riches} advancement when the player is standing in
     * a chunk whose biome is an oil-design biome (rich or excessive tier) AND
     * any chunk within {@link #FINE_RICHES_SCAN_RADIUS} of them actually rolled
     * oil per {@link OilGenerator#getStructures}. Two distinct things have to
     * be true at once: the player has reached an oil biome (the description
     * literally says "Find an oil biome"), AND there's actually oil at or near
     * them so the advancement isn't lying.
     * <p>
     * Why we switched off {@code ChunkWatchEvent.Watch}: that event fires for
     * every chunk inside the player's render distance — at render distance 32
     * that's 4225 chunks. The handler then evaluated the *watched* chunk's
     * biome, not the player's, so spawning in a savanna next to an ocean
     * granted the advancement instantly because the deep ocean chunks 30
     * chunks away happened to be visible. The semantic should be "you walked
     * to an oil biome," not "you can see one." Player tick + check-own-chunk
     * fixes that. The neighbourhood scan keeps the user-friendly "you don't
     * have to stand exactly on the oil block" feel and also covers the
     * tiny-rich-biome-patch case (1-chunk deep ocean / desert) — the player
     * stands on the patch and oil rolled in any nearby chunk still grants.
     * Radius is sized so the player swimming through a LAKE-tier oil pool
     * whose origin chunk is several chunks away (tendrils can reach ~3 chunks)
     * still gets the advancement; with a tighter radius the player could
     * literally swim through visible oil and only get the advancement when
     * directly on top of the origin chunk.
     * <p>
     * The neighbourhood scan is biome-agnostic (uses
     * {@link OilGenerator#wouldGenerateOilForOriginChunk}, not the
     * InOilBiome variant). Rationale: once we know the player is IN an oil
     * biome, any nearby rolled-oil chunk demonstrates "there's oil here." We
     * don't additionally gate the neighbour chunks on biome because oil that
     * spills from a rich-biome origin into an adjacent non-rich chunk is
     * still valid evidence the player has found oil in their oil biome.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % FINE_RICHES_TICK_STRIDE != 0) return;
        // ServerPlayer is server-only by construction, so player.level() is a ServerLevel.
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!OilGenerator.canGenerateOilIn(level)) return;

        ChunkPos current = player.chunkPosition();
        int cx = buildcraft.lib.misc.PositionUtil.chunkX(current);
        int cz = buildcraft.lib.misc.PositionUtil.chunkZ(current);

        // Player must currently BE in an oil-design biome chunk.
        if (!OilGenerator.isOilDesignBiomeAt(level, cx, cz)) return;

        // ... and some chunk in the neighbourhood must have rolled oil.
        for (int dx = -FINE_RICHES_SCAN_RADIUS; dx <= FINE_RICHES_SCAN_RADIUS; dx++) {
            for (int dz = -FINE_RICHES_SCAN_RADIUS; dz <= FINE_RICHES_SCAN_RADIUS; dz++) {
                if (OilGenerator.wouldGenerateOilForOriginChunk(level, cx + dx, cz + dz)) {
                    AdvancementUtil.unlockAdvancement(player, ADVANCEMENT_FINE_RICHES);
                    return;
                }
            }
        }
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
