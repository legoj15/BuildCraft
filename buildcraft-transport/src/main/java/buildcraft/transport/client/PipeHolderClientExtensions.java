package buildcraft.transport.client;

import net.minecraft.client.Minecraft;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

import org.jspecify.annotations.Nullable;

import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.lib.misc.SpriteUtil;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Custom client extensions for the pipe_holder block.
 * Overrides break/hit particle effects to use the correct pipe texture
 * instead of the static default (which is hardcoded to wood pipe).
 */
public class PipeHolderClientExtensions implements IClientBlockExtensions {
    public static final PipeHolderClientExtensions INSTANCE = new PipeHolderClientExtensions();

    private PipeHolderClientExtensions() {}

    /**
     * Resolves the correct particle sprite for the pipe at the given position.
     * Falls back to null if the pipe or definition is unavailable.
     */
    private @Nullable TextureAtlasSprite getPipeSprite(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TilePipeHolder tile && tile.getPipe() != null) {
            PipeDefinition def = tile.getPipe().getDefinition();
            if (def.textures != null && def.textures.length > 0) {
                TextureAtlasSprite sprite = SpriteUtil.getSprite(def.textures[0]);
                if (sprite != null && sprite != SpriteUtil.missingSprite()) {
                    return sprite;
                }
            }
        }
        return null;
    }

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        TextureAtlasSprite sprite = getPipeSprite(level, pos);
        if (sprite == null) {
            return false; // fall back to default
        }

        // Spawn 4×4×4 = 64 particles, mirroring vanilla ParticleEngine.destroy()
        for (int ix = 0; ix < 4; ix++) {
            for (int iy = 0; iy < 4; iy++) {
                for (int iz = 0; iz < 4; iz++) {
                    double x = pos.getX() + (ix + 0.5) / 4.0;
                    double y = pos.getY() + (iy + 0.5) / 4.0;
                    double z = pos.getZ() + (iz + 0.5) / 4.0;
                    PipeBreakParticle particle = new PipeBreakParticle(
                            (ClientLevel) level, x, y, z,
                            x - pos.getX() - 0.5,
                            y - pos.getY() - 0.5,
                            z - pos.getZ() - 0.5,
                            sprite
                    );
                    manager.add(particle);
                }
            }
        }
        return true; // suppress default particles
    }

    @Override
    public boolean addHitEffects(BlockState state, Level level, @Nullable HitResult target, ParticleEngine manager) {
        // For hit effects, we let vanilla handle the particle spawning
        // but the destroy effect (which is much more visible) uses the correct texture.
        return false;
    }

    /**
     * Spawns a sprint particle with the correct pipe texture.
     * Called from BlockPipeHolder.addRunningEffects.
     * @return true if a particle was spawned, false to fall back to vanilla.
     */
    public static boolean spawnRunningParticle(Level level, BlockPos pos, double entityX, double entityZ,
                                               double entityWidth, double motionX, double motionZ, double minY) {
        if (!level.isClientSide()) return false;
        var be = level.getBlockEntity(pos);
        if (!(be instanceof TilePipeHolder tile) || tile.getPipe() == null) return false;

        PipeDefinition def = tile.getPipe().getDefinition();
        TextureAtlasSprite sprite = null;
        if (def.textures != null && def.textures.length > 0) {
            sprite = SpriteUtil.getSprite(def.textures[0]);
            if (sprite == SpriteUtil.missingSprite()) sprite = null;
        }
        if (sprite == null) return false;

        var random = level.getRandom();
        double x = entityX + (random.nextFloat() - 0.5) * entityWidth;
        double y = minY + 0.1;
        double z = entityZ + (random.nextFloat() - 0.5) * entityWidth;

        PipeBreakParticle particle = new PipeBreakParticle(
            (ClientLevel) level, x, y, z,
            -motionX * 4.0, 1.5, -motionZ * 4.0,
            sprite
        );
        particle.setLifetime(particle.getLifetime() / 2);
        Minecraft.getInstance().particleEngine.add(particle);
        return true;
    }

    /**
     * A simple particle that renders from the block texture atlas with a given sprite,
     * bypassing TerrainParticle's updateSprite() which would re-resolve from the block model.
     * Randomises UV sub-regions so each particle shows a small chunk of the texture.
     */
    private static class PipeBreakParticle extends SingleQuadParticle {
        private final float u0, u1, v0, v1;

        PipeBreakParticle(ClientLevel level, double x, double y, double z,
                          double xd, double yd, double zd,
                          TextureAtlasSprite sprite) {
            super(level, x, y, z, xd, yd, zd, sprite);
            this.gravity = 1.0F;
            this.quadSize /= 2.0F;

            // Pick a random 1/4 x 1/4 sub-region of the sprite (like TerrainParticle)
            float uRange = sprite.getU1() - sprite.getU0();
            float vRange = sprite.getV1() - sprite.getV0();
            float cellU = uRange / 4.0F;
            float cellV = vRange / 4.0F;
            this.u0 = sprite.getU0() + this.random.nextInt(4) * cellU;
            this.v0 = sprite.getV0() + this.random.nextInt(4) * cellV;
            this.u1 = this.u0 + cellU;
            this.v1 = this.v0 + cellV;
        }

        @Override protected float getU0() { return u0; }
        @Override protected float getU1() { return u1; }
        @Override protected float getV0() { return v0; }
        @Override protected float getV1() { return v1; }

        @Override
        protected Layer getLayer() {
            return Layer.TERRAIN;
        }
    }
}
