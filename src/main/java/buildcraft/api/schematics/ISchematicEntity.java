package buildcraft.api.schematics;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import buildcraft.api.core.InvalidInputDataException;

public interface ISchematicEntity extends ISchematic<SchematicEntityContext> {
    @Override
    void init(SchematicEntityContext context);

    Vec3 getPos();

    @Nonnull
    default List<ItemStack> computeRequiredItems() {
        return Collections.emptyList();
    }

    /**
     * Mode-aware pricing used by the Builder: {@code includeContainerContents} is false in its IGNORE
     * container-contents mode, where what the entity holds (an armor stand's gear, an item frame's item, a storage
     * minecart's inventory) is neither placed nor charged. The default ignores the flag; a schematic that can tell
     * contents from the entity itself overrides this and {@link #build(Level, BlockPos, boolean)} together.
     */
    @Nonnull
    default List<ItemStack> computeRequiredItems(boolean includeContainerContents) {
        return computeRequiredItems();
    }

    @Nonnull
    default List<FluidStack> computeRequiredFluids() {
        return Collections.emptyList();
    }

    ISchematicEntity getRotated(Rotation rotation);

    Entity build(Level world, BlockPos basePos);

    /** Mode-aware {@link #build(Level, BlockPos)}: when {@code includeContainerContents} is false the entity is
     *  spawned without what it holds, matching {@link #computeRequiredItems(boolean)}. The default ignores the flag. */
    default Entity build(Level world, BlockPos basePos, boolean includeContainerContents) {
        return build(world, basePos);
    }

    Entity buildWithoutChecks(Level world, BlockPos basePos);

    CompoundTag serializeNbt();

    /** @throws InvalidInputDataException If the input data wasn't correct or didn't make sense. */
    void deserializeNbt(CompoundTag nbt) throws InvalidInputDataException;
}

