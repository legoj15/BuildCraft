package buildcraft.api.schematics;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;

public interface ISchematicBlock {
    void init(SchematicBlockContext context);

    default boolean isAir() {
        return false;
    }

    @Nonnull
    default Set<BlockPos> getRequiredBlockOffsets() {
        return Collections.emptySet();
    }

    @Nonnull
    default List<ItemStack> computeRequiredItems() {
        return Collections.emptyList();
    }

    @Nonnull
    default List<FluidStack> computeRequiredFluids() {
        return Collections.emptyList();
    }

    /**
     * Returns the {@link BlockState} that represents this schematic, purely for rendering
     * purposes (e.g. the blueprint tooltip preview). Implementations that don't correspond to a
     * single BlockState (air, pure-fluid cells, custom logical schematics) should return
     * {@code null} so the renderer can skip them.
     * <p>
     * Not used during actual world-building — that path goes through
     * {@link #build(Level, BlockPos)} which may mutate state, place entities, etc.
     */
    @Nullable
    default BlockState getBlockStateForRender() {
        return null;
    }

    ISchematicBlock getRotated(Rotation rotation);

    boolean canBuild(Level world, BlockPos blockPos);

    default boolean isReadyToBuild(Level world, BlockPos blockPos) {
        return true;
    }

    boolean build(Level world, BlockPos blockPos);

    boolean buildWithoutChecks(Level world, BlockPos blockPos);

    boolean isBuilt(Level world, BlockPos blockPos);

    CompoundTag serializeNBT();

    /** @throws InvalidInputDataException If the input data wasn't correct or didn't make sense. */
    void deserializeNBT(CompoundTag nbt) throws InvalidInputDataException;
}

