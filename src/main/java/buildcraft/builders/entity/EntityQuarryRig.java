package buildcraft.builders.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.AABB;

/**
 * Invisible structural entity that provides physical collision for the Quarry's
 * moving arms. Modelled after AbstractBoat's collision contract:
 * <ul>
 *   <li>{@link #canBeCollidedWith(Entity)} — the physics gate; returns true so
 *       the player's movement code treats this entity as a solid obstacle.</li>
 *   <li>{@link #isPickable()} — the raycast gate; also true so the entity can
 *       be targeted / clicked.</li>
 *   <li>{@link #isPushable()} — false; the quarry arms are immovable.</li>
 * </ul>
 */
public class EntityQuarryRig extends Entity {
    private static final EntityDataAccessor<Boolean> PHASING = SynchedEntityData.defineId(EntityQuarryRig.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SIZE_X = SynchedEntityData.defineId(EntityQuarryRig.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE_Y = SynchedEntityData.defineId(EntityQuarryRig.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE_Z = SynchedEntityData.defineId(EntityQuarryRig.class, EntityDataSerializers.FLOAT);

    public boolean phasing = false;

    public EntityQuarryRig(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASING, false);
        builder.define(SIZE_X, 0f);
        builder.define(SIZE_Y, 0f);
        builder.define(SIZE_Z, 0f);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // ── Collision contract (mirrors AbstractBoat) ─────────────────────────

    /**
     * The physics collision gate. Called by Entity.canCollideWith(other) to
     * decide whether 'other' (e.g. the player) should treat *this* entity as
     * a solid obstacle during movement resolution.  Base Entity returns false;
     * we return true (unless phasing).
     */
    @Override
    public boolean canBeCollidedWith(Entity other) {
        return !phasing;
    }

    /** Raycast / picking gate — allows clicking on the entity. */
    @Override
    public boolean isPickable() {
        return !phasing;
    }

    /** Quarry arms are immovable structures. */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.damagesource.DamageSource source,
                              float amount) {
        return false;
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.phasing = this.entityData.get(PHASING);
            float halfX = this.entityData.get(SIZE_X) / 2.0f;
            float halfY = this.entityData.get(SIZE_Y) / 2.0f;
            float halfZ = this.entityData.get(SIZE_Z) / 2.0f;
            
            if (halfX > 0) {
                net.minecraft.world.phys.Vec3 pos = this.position();
                this.setBoundingBox(new AABB(
                    pos.x - halfX, pos.y - halfY, pos.z - halfZ,
                    pos.x + halfX, pos.y + halfY, pos.z + halfZ
                ));
            }
        }
    }

    // ── Setters (server-side) ────────────────────────────────────────────

    public void setPhasing(boolean phase) {
        this.phasing = phase;
        this.entityData.set(PHASING, phase);
    }

    /**
     * Sets the bounding box for this arm collision entity.
     * Called every tick by TileQuarry on the server.
     */
    public void setRiggingBox(AABB aabb) {
        // Sync center position. The base Entity will handle networking/interpolation.
        this.setPos((aabb.minX + aabb.maxX) / 2.0, (aabb.minY + aabb.maxY) / 2.0, (aabb.minZ + aabb.maxZ) / 2.0);
        this.setBoundingBox(aabb);

        this.entityData.set(SIZE_X, (float) (aabb.maxX - aabb.minX));
        this.entityData.set(SIZE_Y, (float) (aabb.maxY - aabb.minY));
        this.entityData.set(SIZE_Z, (float) (aabb.maxZ - aabb.minZ));
    }
}
