package buildcraft.builders.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
//? if >=1.21.10 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?}
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
 *
 * <h3>Why we override {@code makeBoundingBox}:</h3>
 * {@link Entity#setPos} calls {@code makeBoundingBox()} to rebuild the AABB
 * from the entity type's default dimensions (1×1).  The vanilla networking
 * layer calls {@code setPos} every position-sync tick, so any custom AABB set
 * via {@code setBoundingBox()} gets silently replaced with a 1×1 box.  This
 * caused a periodic one-frame collision gap (the "phasing" glitch) every time
 * the server sent a position update.  By overriding {@code makeBoundingBox} to
 * use our synched dimensions, every call to {@code setPos} — whether from our
 * code or from the networking layer — produces the correct AABB.
 */
public class EntityQuarryRig extends Entity {
    private static final EntityDataAccessor<Boolean> PHASING = SynchedEntityData.defineId(EntityQuarryRig.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SIZE_X = SynchedEntityData.defineId(EntityQuarryRig.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE_Y = SynchedEntityData.defineId(EntityQuarryRig.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SIZE_Z = SynchedEntityData.defineId(EntityQuarryRig.class, EntityDataSerializers.FLOAT);
    /**
     * When true the box hangs DOWN from the entity position instead of being centred on it (so the AABB
     * is {@code [pos.y - SIZE_Y, pos.y]}). Used for the tall vertical drill column: anchoring it at the
     * TOP keeps the entity's <em>position</em> — and therefore the entity-storage section MC scans for
     * collision — up at the frame top where the player stands, even when the column extends ~hundreds of
     * blocks down. A column centred on its midpoint puts its position section far below the player, and
     * {@code EntitySectionStorage.forEachAccessibleNonEmptySection} only scans sections within ~a few
     * blocks of the query — so the player never finds it and the fully-extended arm has no collision.
     */
    private static final EntityDataAccessor<Boolean> ANCHOR_TOP = SynchedEntityData.defineId(EntityQuarryRig.class, EntityDataSerializers.BOOLEAN);

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
        builder.define(ANCHOR_TOP, false);
    }

    @Override
    //? if >=1.21.10 {
    protected void readAdditionalSaveData(ValueInput input) {}
    //?} else {
    /*protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag input) {}*/
    //?}

    @Override
    //? if >=1.21.10 {
    protected void addAdditionalSaveData(ValueOutput output) {}
    //?} else {
    /*protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag output) {}*/
    //?}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    // ── Custom AABB ──────────────────────────────────────────────────────

    /**
     * Overrides the base Entity's bounding box factory so that every call to
     * {@code setPos()} builds our custom-sized AABB instead of the default
     * 1×1 box from the EntityType dimensions.  This is the key fix for the
     * periodic phasing glitch — the networking layer's position-sync calls
     * {@code setPos()}, which calls this method, so the AABB is always correct.
     */
    @Override
    //? if >=1.21.10 {
    protected AABB makeBoundingBox(Vec3 position) {
    //?} else {
    /*protected AABB makeBoundingBox() {
        net.minecraft.world.phys.Vec3 position = this.position();*/
    //?}
        float sizeY = this.entityData.get(SIZE_Y);
        float halfX = this.entityData.get(SIZE_X) / 2.0f;
        float halfZ = this.entityData.get(SIZE_Z) / 2.0f;

        // Before dimensions are set (SIZE_X is 0), fall back to default
        if (halfX <= 0) {
            //? if >=1.21.10 {
            return super.makeBoundingBox(position);
            //?} else {
            /*return super.makeBoundingBox();*/
            //?}
        }

        // The column hangs straight down from its anchor (position.y is the TOP); the beams are centred.
        double minY = this.entityData.get(ANCHOR_TOP) ? position.y - sizeY : position.y - sizeY / 2.0;
        double maxY = this.entityData.get(ANCHOR_TOP) ? position.y : position.y + sizeY / 2.0;
        return new AABB(
            position.x - halfX, minY, position.z - halfZ,
            position.x + halfX, maxY, position.z + halfZ
        );
    }

    // ── Collision contract (mirrors AbstractBoat) ─────────────────────────

    /**
     * The physics collision gate. Called by Entity.canCollideWith(other) to
     * decide whether 'other' (e.g. the player) should treat *this* entity as
     * a solid obstacle during movement resolution.  Base Entity returns false;
     * we return true (unless phasing).
     */
    @Override
    //? if >=1.21.10 {
    public boolean canBeCollidedWith(Entity other) {
    //?} else {
    /*public boolean canBeCollidedWith() {*/
    //?}
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
    //? if >=1.21.10 {
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.damagesource.DamageSource source,
                              float amount) {
    //?} else {
    /*public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {*/
    //?}
        return false;
    }

    /**
     * Fire-immune on every node. This is an invisible structural collision entity for the drill arms;
     * when the arms pass through lava it would otherwise catch fire and the entity render dispatcher
     * would draw the fire overlay sprite over the (invisible) entity — i.e. floating fire across the
     * middle of the drill arm. Being fire-immune skips lava ignition entirely, so no overlay and no
     * fire ticks.
     */
    @Override
    public boolean fireImmune() {
        return true;
    }

    //? if <1.21.10 {
    /*// 1.21.1 only: snap to each synced position instead of smoothing. The classic client entity lerp
    // (lerpTo with a fixed multi-step interpolation) made this collision entity lag visibly behind the
    // BER-rendered drill arm — the visual follows the block entity's per-tick clientDrillPos, so the
    // multi-tick lerp left the collision "somewhere other than where it visually is". With
    // updateInterval(1) the rig gets a position every tick, so snapping tracks the visual within a
    // tick. 1.21.10+/26.1 use the adaptive InterpolationHandler, which already keeps pace — so this
    // override is 1.21.1-only.
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
    }*/
    //?}

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.phasing = this.entityData.get(PHASING);
        }
    }

    /**
     * Rebuild the collision box from the synched dimensions whenever they change on the client.
     *
     * <p>{@link #makeBoundingBox} is only invoked by {@code setPos} — i.e. on a POSITION update. But the
     * synched {@code SIZE_*} values arrive in a separate packet a tick after this entity's spawn packet,
     * and a boom-arm beam that isn't moving in its own axis (e.g. the X-beam while the drill is doing a
     * Z-pass) receives no position update to pick up the new size. Without this hook its box would stay at
     * the stale spawn size — the {@code halfX <= 0} 1×1 fallback — until the next periodic re-sync,
     * leaving a gap in the rig's collision (small frames make it a couple of blocks; it only shows when
     * an arm happens to be idle right after the entity (re)spawns, e.g. after a world reload). Re-applying
     * the current position here refreshes the AABB the moment the size syncs. Client-only: the server
     * positions the rig via {@code TileQuarry.setRiggingBox}, which already setPos-es after setting size.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide()
                && (SIZE_X.equals(key) || SIZE_Y.equals(key) || SIZE_Z.equals(key) || ANCHOR_TOP.equals(key))) {
            this.setPos(this.getX(), this.getY(), this.getZ());
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
     *
     * <p>Updates the synched size data <em>before</em> calling {@code setPos()},
     * so that {@code makeBoundingBox()} (called inside {@code setPos()}) already
     * has the correct dimensions available.  This eliminates the previous
     * pattern of setPos → wrong AABB → setBoundingBox → correct AABB.
     */
    public void setRiggingBox(AABB aabb) {
        // Update synched size data first so makeBoundingBox() uses correct values
        this.entityData.set(SIZE_X, (float) (aabb.maxX - aabb.minX));
        this.entityData.set(SIZE_Y, (float) (aabb.maxY - aabb.minY));
        this.entityData.set(SIZE_Z, (float) (aabb.maxZ - aabb.minZ));
        this.entityData.set(ANCHOR_TOP, false);

        // Now setPos will call makeBoundingBox() which reads the updated sizes
        this.setPos((aabb.minX + aabb.maxX) / 2.0, (aabb.minY + aabb.maxY) / 2.0, (aabb.minZ + aabb.maxZ) / 2.0);
    }

    /**
     * Like {@link #setRiggingBox} but positions the entity at the box's TOP face, with the box hanging
     * straight down from there ({@link #ANCHOR_TOP}). For the tall vertical drill column: keeps the
     * entity's position (and its entity-storage section) up at the frame top near the player so the
     * collision query actually scans it, instead of burying it at the column midpoint hundreds of blocks
     * below — see {@link #ANCHOR_TOP}.
     */
    public void setRiggingBoxAnchoredTop(AABB aabb) {
        this.entityData.set(SIZE_X, (float) (aabb.maxX - aabb.minX));
        this.entityData.set(SIZE_Y, (float) (aabb.maxY - aabb.minY));
        this.entityData.set(SIZE_Z, (float) (aabb.maxZ - aabb.minZ));
        this.entityData.set(ANCHOR_TOP, true);

        // Position at the top face; makeBoundingBox() (ANCHOR_TOP) then extends the box down by SIZE_Y.
        this.setPos((aabb.minX + aabb.maxX) / 2.0, aabb.maxY, (aabb.minZ + aabb.maxZ) / 2.0);
    }
}
