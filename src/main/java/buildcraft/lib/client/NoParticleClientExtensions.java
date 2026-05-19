package buildcraft.lib.client;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

import org.jspecify.annotations.Nullable;

/**
 * IClientBlockExtensions implementation that fully suppresses break and hit
 * particles. Returning true tells NeoForge "I handled it" — spawning nothing
 * then leaves the block silent on the particle side. Use for blocks with
 * RenderShape.INVISIBLE whose visuals come from something other than the
 * block model (e.g. tubes rendered as lasers), where vanilla's model-particle
 * fallback would otherwise spawn confusing cube_all-textured particles.
 * Running/landing particles live on Block itself, not IClientBlockExtensions —
 * if a consumer needs to silence those too, override them on the block class.
 */
public class NoParticleClientExtensions implements IClientBlockExtensions {
    public static final NoParticleClientExtensions INSTANCE = new NoParticleClientExtensions();

    private NoParticleClientExtensions() {}

    @Override
    public boolean addHitEffects(BlockState state, Level level, @Nullable HitResult target, ParticleEngine manager) {
        return true;
    }

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        return true;
    }
}
