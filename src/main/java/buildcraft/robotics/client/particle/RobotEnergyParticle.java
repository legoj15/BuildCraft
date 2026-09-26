/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.robotics.client.particle;

import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import buildcraft.robotics.particle.RobotEnergyParticleOptions;

/**
 * The robot's red energy exhaust — a re-creation of 7.1.x's {@code EntityRobotEnergyParticle}.
 *
 * <p>That class was vanilla 1.7.10's smoke particle with three changes: a dark-red tint ({@code red = rand *
 * 0.6}, green and blue zero), a size multiplier that also stretched its lifetime, and a far gentler drift
 * ({@code +0.0005} up per tick, horizontal damping only). Modern vanilla's {@link BaseAshSmokeParticle} is the
 * direct descendant of that smoke — same random start motion scaled by 0.1, same {@code 0.75 * size} quad,
 * same {@code 16 / (rand * 0.8 + 0.2) * size} lifetime, same grow-in over the first 1/32 of life, same
 * big-to-small walk through the {@code generic_7..0} frames — so this subclasses it for all of that (and for
 * its render layer, whose API differs by MC line) and overrides only the colour and the motion.
 */
public class RobotEnergyParticle extends BaseAshSmokeParticle {
    /** 7.1.x's per-tick upward drift. */
    private static final double RISE_PER_TICK = 0.0005;
    /** 7.1.x's horizontal damping; its vertical motion was never damped. */
    private static final double HORIZONTAL_DRAG = 0.98;
    private static final float MAX_RED = 0.6F;

    private final SpriteSet frames;

    protected RobotEnergyParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            float size, SpriteSet sprites) {
        // colorRandom, gravity and friction are placeholders: the colour is set below and tick() owns motion.
        super(level, x, y, z, 0.1F, 0.1F, 0.1F, xd, yd, zd, size, sprites, 0F, 16, 0F, true);
        this.frames = sprites;
        this.rCol = this.random.nextFloat() * MAX_RED;
        this.gCol = 0F;
        this.bCol = 0F;
    }

    /** 7.1.x's {@code onUpdate}, verbatim in effect: vanilla's tick would also damp the vertical motion (so a
     *  downward exhaust would stall instead of carrying to the ground) and speed up on a blocked ceiling.
     *
     *  <p>Note on size: the random per-puff size variation this inherits ({@code Particle}'s {@code quadSize}
     *  seed of 1..2x) is 7.1.x-faithful — 1.7.10's {@code EntityFX} seeded {@code particleScale} with the
     *  same {@code (rand * 0.5 + 0.5) * 2} before the robot particle multiplied it. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.setSpriteFromAge(this.frames);
        this.move(this.xd, this.yd, this.zd);
        this.xd *= HORIZONTAL_DRAG;
        this.yd += RISE_PER_TICK;
        this.zd *= HORIZONTAL_DRAG;
        if (this.onGround) {
            this.xd *= 0.7;
            this.zd *= 0.7;
        }
    }

    /** 1.7.10's {@code Entity.moveEntity} semantics: a blocked axis loses its velocity and the puff keeps
     *  going on the others. Vanilla's {@code Particle.move} instead latches a private "stopped by collision"
     *  flag the first time a vertical move is fully blocked and never moves again — the (default, downward)
     *  exhaust would freeze where it lands instead of skidding and lifting off on its slow rise, and an
     *  upward one would stick to a ceiling. The null collision source compiles against every line (26.2 added
     *  a {@code CollisionContext} overload; the cast picks the {@code @Nullable Entity} one). */
    @Override
    public void move(double xa, double ya, double za) {
        double wantX = xa;
        double wantY = ya;
        double wantZ = za;
        if (this.hasPhysics && (xa != 0.0 || ya != 0.0 || za != 0.0)) {
            Vec3 allowed = Entity.collideBoundingBox((Entity) null, new Vec3(xa, ya, za), this.getBoundingBox(),
                    this.level, List.of());
            xa = allowed.x;
            ya = allowed.y;
            za = allowed.z;
        }
        if (xa != 0.0 || ya != 0.0 || za != 0.0) {
            this.setBoundingBox(this.getBoundingBox().move(xa, ya, za));
            this.setLocationFromBoundingbox();
        }
        this.onGround = wantY != ya && wantY < 0.0;
        if (wantX != xa) {
            this.xd = 0.0;
        }
        if (wantY != ya) {
            this.yd = 0.0;
        }
        if (wantZ != za) {
            this.zd = 0.0;
        }
    }

    /** Registered with {@code RegisterParticleProvidersEvent.registerSpriteSet}; the sprite set is the
     *  {@code generic_7..0} list in {@code particles/robot_energy.json}. */
    public static class Provider implements ParticleProvider<RobotEnergyParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        // 1.21.10 added the RandomSource parameter; the particle rolls its own randoms either way.
        //? if >=1.21.10 {
        @Override
        public Particle createParticle(RobotEnergyParticleOptions options, ClientLevel level, double x, double y,
                double z, double xd, double yd, double zd, net.minecraft.util.RandomSource random) {
            return new RobotEnergyParticle(level, x, y, z, xd, yd, zd, options.size(), sprites);
        }
        //?} else {
        /*@Override
        public Particle createParticle(RobotEnergyParticleOptions options, ClientLevel level, double x, double y,
                double z, double xd, double yd, double zd) {
            return new RobotEnergyParticle(level, x, y, z, xd, yd, zd, options.size(), sprites);
        }*/
        //?}
    }
}
