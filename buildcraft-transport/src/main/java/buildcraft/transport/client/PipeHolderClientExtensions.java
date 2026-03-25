package buildcraft.transport.client;

import net.minecraft.client.Minecraft;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

import org.jspecify.annotations.Nullable;

import buildcraft.api.transport.pipe.PipeDefinition;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.lib.misc.SpriteUtil;
import buildcraft.transport.block.BlockPipeHolder;
import buildcraft.transport.tile.TilePipeHolder;

/**
 * Custom client extensions for the pipe_holder block.
 * Overrides break/hit/sprint particle effects to use the correct pipe or
 * pluggable texture instead of the static default (pipe_holder.json particle).
 */
public class PipeHolderClientExtensions implements IClientBlockExtensions {
    public static final PipeHolderClientExtensions INSTANCE = new PipeHolderClientExtensions();

    /** Reusable render state to avoid allocation per particle. */
    private final ItemStackRenderState renderState = new ItemStackRenderState();

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

    /**
     * Resolves the particle sprite for a pluggable using 1.21.11's ItemStackRenderState.
     * This correctly handles all item types by resolving through the item model system.
     */
    private @Nullable TextureAtlasSprite getPluggableSprite(PipePluggable pluggable) {
        ItemStack stack = pluggable.getPickStack();
        if (!stack.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            renderState.clear();
            ItemModelResolver resolver = mc.getItemModelResolver();
            resolver.appendItemLayers(renderState, stack,
                    net.minecraft.world.item.ItemDisplayContext.GUI,
                    mc.level, (net.minecraft.world.entity.ItemOwner) null, 0);
            // MC 26.1: pickParticleIcon → pickParticleMaterial (returns Material.Baked)
            var particleMat = renderState.pickParticleMaterial(mc.level.getRandom());
            TextureAtlasSprite sprite = particleMat != null ? particleMat.sprite() : null;
            if (sprite != null && sprite != SpriteUtil.missingSprite()) {
                return sprite;
            }
        }
        return null;
    }

    /**
     * Resolves the best sprite for a specific hit on the pipe_holder block.
     * Uses hit location and pluggable AABB testing to determine if a pluggable was hit.
     */
    private @Nullable TextureAtlasSprite getSpriteForHit(Level level, BlockPos pos, BlockHitResult blockHit) {
        if (level.getBlockEntity(pos) instanceof TilePipeHolder tile) {
            // Use hit location to determine which pluggable (if any) was hit
            double lx = blockHit.getLocation().x - pos.getX();
            double ly = blockHit.getLocation().y - pos.getY();
            double lz = blockHit.getLocation().z - pos.getZ();
            Direction plugDir = BlockPipeHolder.getHitPluggable(tile, lx, ly, lz);
            if (plugDir != null) {
                PipePluggable plug = tile.getPluggable(plugDir);
                if (plug != null) {
                    TextureAtlasSprite sprite = getPluggableSprite(plug);
                    if (sprite != null) return sprite;
                }
            }
        }
        // Fall back to pipe sprite
        return getPipeSprite(level, pos);
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
        if (target instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            TextureAtlasSprite sprite = getSpriteForHit(level, pos, blockHit);
            if (sprite != null) {
                Direction face = blockHit.getDirection();
                spawnHitParticle(level, pos, face, sprite, manager);
                return true; // suppress default particles
            }
        }
        return false;
    }

    /**
     * Spawns a single hit particle on the given face of the block.
     * Mirrors vanilla ParticleEngine.crack() positioning logic.
     */
    private void spawnHitParticle(Level level, BlockPos pos, Direction face,
                                  TextureAtlasSprite sprite, ParticleEngine manager) {
        var random = level.getRandom();
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();

        // Offset particle to be on the face surface
        double offset = 0.1;
        switch (face) {
            case DOWN -> y = pos.getY() - offset;
            case UP -> y = pos.getY() + 1.0 + offset;
            case NORTH -> z = pos.getZ() - offset;
            case SOUTH -> z = pos.getZ() + 1.0 + offset;
            case WEST -> x = pos.getX() - offset;
            case EAST -> x = pos.getX() + 1.0 + offset;
        }

        PipeBreakParticle particle = new PipeBreakParticle(
                (ClientLevel) level, x, y, z, 0, 0, 0, sprite
        );
        particle.setLifetime(4);
        particle.setParticleSpeed(0, 0, 0);
        manager.add(particle);
    }

    /**
     * Spawns a sprint particle with the correct pipe or pluggable texture.
     * Called from BlockPipeHolder.addRunningEffects.
     * If a pluggable exists on the UP face, uses its texture instead of the pipe's.
     * @return true if a particle was spawned, false to fall back to vanilla.
     */
    public static boolean spawnRunningParticle(Level level, BlockPos pos, double entityX, double entityZ,
                                               double entityWidth, double motionX, double motionZ, double minY) {
        if (!level.isClientSide()) return false;
        var be = level.getBlockEntity(pos);
        if (!(be instanceof TilePipeHolder tile) || tile.getPipe() == null) return false;

        // Check if there's a pluggable on the UP face (entity is running on top)
        TextureAtlasSprite sprite = null;
        PipePluggable upPlug = tile.getPluggable(Direction.UP);
        if (upPlug != null) {
            sprite = INSTANCE.getPluggableSprite(upPlug);
        }

        // Fall back to pipe texture
        if (sprite == null) {
            PipeDefinition def = tile.getPipe().getDefinition();
            if (def.textures != null && def.textures.length > 0) {
                sprite = SpriteUtil.getSprite(def.textures[0]);
                if (sprite == SpriteUtil.missingSprite()) sprite = null;
            }
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
            return Layer.OPAQUE_TERRAIN;
        }
    }
}
