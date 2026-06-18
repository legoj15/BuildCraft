/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.silicon.tile;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;

import buildcraft.api.core.EnumPipePart;
import buildcraft.api.recipes.AssemblyRecipe;

import buildcraft.lib.misc.BCValueInput;
import buildcraft.lib.misc.BCValueOutput;
import buildcraft.lib.misc.GameProfileUtil;
import buildcraft.lib.misc.InventoryUtil;
import buildcraft.lib.misc.AdvancementUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.recipe.AssemblyRecipeRegistry;
import buildcraft.lib.tile.item.ItemHandlerManager;
import buildcraft.lib.tile.item.ItemHandlerSimple;

import buildcraft.silicon.BCSiliconBlockEntities;
import buildcraft.silicon.EnumAssemblyRecipeState;

@SuppressWarnings("deprecation")
public class TileAssemblyTable extends TileLaserTableBase {
    private static final Identifier ADVANCEMENT = Identifier.parse("buildcraftunofficial:precision_crafting");

    public final ItemHandlerSimple inv = itemManager.addInvHandler(
        "inv",
        3 * 4,
        // INSERT-only by design (commit a2bb0ee67), NOT 1.12.2's BOTH: the table has no pipe-facing
        // output slot (it auto-ejects finished output, like a quarry), so the input inventory is a
        // pure sink — making it extractable would let a wooden pipe pull crafting materials back out.
        ItemHandlerManager.EnumAccess.INSERT,
        EnumPipePart.VALUES
    );
    public SortedMap<AssemblyInstruction, EnumAssemblyRecipeState> recipesStates = new TreeMap<>();

    public TileAssemblyTable(BlockPos pos, BlockState state) {
        super(BCSiliconBlockEntities.ASSEMBLY_TABLE.get(), pos, state);
    }

    private void updateRecipes() {
        int count = recipesStates.size();
        for (AssemblyRecipe recipe : AssemblyRecipeRegistry.REGISTRY.values()) {
            Set<ItemStack> outputs = recipe.getOutputs(inv.stacks);
            for (ItemStack out : outputs) {
                boolean found = false;
                for (AssemblyInstruction instruction : recipesStates.keySet()) {
                    if (instruction.recipe == recipe && out == instruction.output) {
                        found = true;
                        break;
                    }
                }
                AssemblyInstruction instruction = new AssemblyInstruction(recipe, out);
                boolean alreadyContains = recipesStates.containsKey(instruction);
                if (!found && !alreadyContains) {
                    recipesStates.put(instruction, EnumAssemblyRecipeState.POSSIBLE);
                }
            }
        }

        boolean findActive = false;
        for (Iterator<Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState>> iterator = recipesStates.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState> entry = iterator.next();
            AssemblyInstruction instruction = entry.getKey();
            EnumAssemblyRecipeState state = entry.getValue();
            boolean enough = extract(inv, instruction.recipe.getInputsFor(instruction.output), true, false);
            if (state == EnumAssemblyRecipeState.POSSIBLE) {
                if (!enough) {
                    iterator.remove();
                }
            } else if (state == EnumAssemblyRecipeState.PAUSED) {
                // User-paused: don't promote or demote, only clicks change this
            } else {
                if (enough) {
                    if (state == EnumAssemblyRecipeState.SAVED) {
                        state = EnumAssemblyRecipeState.SAVED_ENOUGH;
                    }
                } else {
                    if (state != EnumAssemblyRecipeState.SAVED) {
                        state = EnumAssemblyRecipeState.SAVED;
                    }
                }
            }
            if (state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) {
                findActive = true;
            }
            entry.setValue(state);
        }
        if (!findActive) {
            for (Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
                EnumAssemblyRecipeState state = entry.getValue();
                if (state == EnumAssemblyRecipeState.SAVED_ENOUGH) {
                    entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE);
                    break;
                }
            }
        }
    }

    @Nullable
    private AssemblyInstruction getActiveRecipe() {
        return recipesStates.entrySet().stream()
            .filter(entry -> entry.getValue() == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private void activateNextRecipe() {
        AssemblyInstruction activeRecipe = getActiveRecipe();
        if (activeRecipe != null) {
            int index = 0;
            int activeIndex = 0;
            boolean isActiveLast = false;
            long enoughCount = recipesStates.values().stream()
                .filter(state -> state == EnumAssemblyRecipeState.SAVED_ENOUGH || state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE)
                .count();
            if (enoughCount <= 1) {
                return;
            }
            for (Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
                EnumAssemblyRecipeState state = entry.getValue();
                if (state == EnumAssemblyRecipeState.SAVED_ENOUGH) {
                    isActiveLast = false;
                }
                if (state == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) {
                    entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH);
                    activeIndex = index;
                    isActiveLast = true;
                }
                index++;
            }
            index = 0;
            for (Map.Entry<AssemblyInstruction, EnumAssemblyRecipeState> entry : recipesStates.entrySet()) {
                EnumAssemblyRecipeState state = entry.getValue();
                if (state == EnumAssemblyRecipeState.SAVED_ENOUGH && entry.getKey().recipe != activeRecipe.recipe && (index > activeIndex || isActiveLast)) {
                    entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE);
                    break;
                }
                index++;
            }
        }
    }

    @Override
    public long getTarget() {
        return Optional.ofNullable(getActiveRecipe())
            .map(instruction -> instruction.recipe.getRequiredMicroJoulesFor(instruction.output))
            .orElse(0L);
    }

    @Override
    public void serverTick() {
        super.serverTick();

        int prevSize = recipesStates.size();
        int prevHash = recipesStates.hashCode();

        updateRecipes();

        // Sync to clients if recipe states changed
        if (recipesStates.size() != prevSize || recipesStates.hashCode() != prevHash) {
            setChanged();
            if (getLevel() != null) {
                getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        if (getTarget() > 0) {
            // Award precision_crafting on "started a craft" (active recipe exists),
            // not "finished a craft" — matches the 1.12.2 trigger position and the
            // advancement's description ("Start crafting with an assembly table").
            // Fires every tick while a recipe is active until the player has it;
            // tracker.award is idempotent so re-firing is a no-op HashMap lookup.
            if (getOwner() != null) {
                AdvancementUtil.unlockAdvancement(GameProfileUtil.getId(getOwner()), getLevel(), ADVANCEMENT);
            }
            if (power >= getTarget()) {
                AssemblyInstruction instruction = getActiveRecipe();
                if (instruction != null) {
                    extract(inv, instruction.recipe.getInputsFor(instruction.output), false, false);
                    InventoryUtil.addToBestAcceptor(getLevel(), getBlockPos(), null, instruction.output.copy());
                    power -= getTarget();
                    activateNextRecipe();
                }
            }
        }
    }

    // --- Save / Load ---

    @Override
    protected void writeData(BCValueOutput output) {
        super.writeData(output);
        CompoundTag wrapper = new CompoundTag();
        ListTag recipesStatesTag = new ListTag();
        recipesStates.forEach((instruction, state) -> {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("recipe", instruction.recipe.getRegistryName());
            entryTag.putInt("state", state.ordinal());
            // Serialize the output ItemStack so the client can reconstruct the correct variant
            CompoundTag outputTag = NBTUtilBC.itemStackToNBT(instruction.output);
            // Also save the custom data component (for facades, this contains the blockstate info)
            CompoundTag customData = NBTUtilBC.getItemData(instruction.output);
            if (!customData.isEmpty()) {
                outputTag.put("customData", customData);
            }
            entryTag.put("output", outputTag);
            recipesStatesTag.add(entryTag);
        });
        wrapper.put("entries", recipesStatesTag);
        output.store("recipes_states", CompoundTag.CODEC, wrapper);
    }

    @Override
    protected void readData(BCValueInput input) {
        super.readData(input);
        recipesStates.clear();
        input.read("recipes_states", CompoundTag.CODEC).ifPresent(wrapper -> {
            ListTag recipesStatesTag = NBTUtilBC.getList(wrapper, "entries", Tag.TAG_COMPOUND);
            {
                for (int i = 0; i < recipesStatesTag.size(); i++) {
                    CompoundTag entryTag = NBTUtilBC.getCompoundOrNull(recipesStatesTag, i);
                    if (entryTag == null) {
                        continue;
                    }
                    {
                        String name = NBTUtilBC.getString(entryTag, "recipe", "");
                        if (!name.isEmpty()) {
                            AssemblyRecipe recipe = AssemblyRecipeRegistry.REGISTRY.get(name);
                            if (recipe != null) {
                                int stateOrdinal = NBTUtilBC.getInt(entryTag, "state", 0);
                                EnumAssemblyRecipeState[] values = EnumAssemblyRecipeState.values();
                                if (stateOrdinal >= 0 && stateOrdinal < values.length) {
                                    // Try to load the specific output ItemStack from saved data
                                    ItemStack outputStack = ItemStack.EMPTY;
                                    CompoundTag outputTag = NBTUtilBC.getCompoundOrNull(entryTag, "output");
                                    if (outputTag != null) {
                                        outputStack = NBTUtilBC.itemStackFromNBT(outputTag);
                                        // Restore custom data component if present
                                        CompoundTag cd = NBTUtilBC.getCompoundOrNull(outputTag, "customData");
                                        if (cd != null) {
                                            NBTUtilBC.setItemData(outputStack, cd);
                                        }
                                    }

                                    if (outputStack.isEmpty()) {
                                        // Fallback for legacy data: take the first output
                                        Set<ItemStack> outputs = recipe.getOutputs(inv.stacks);
                                        if (!outputs.isEmpty()) {
                                            outputStack = outputs.iterator().next();
                                        }
                                    }
                                    if (!outputStack.isEmpty()) {
                                        AssemblyInstruction instruction = new AssemblyInstruction(recipe, outputStack);
                                        recipesStates.put(instruction, values[stateOrdinal]);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    // --- Inner class ---

    public static class AssemblyInstruction implements Comparable<AssemblyInstruction> {
        public final AssemblyRecipe recipe;
        public final ItemStack output;

        public AssemblyInstruction(AssemblyRecipe recipe, ItemStack output) {
            this.recipe = recipe;
            this.output = output;
        }

        @Override
        public int compareTo(AssemblyInstruction o) {
            int recipeCompare = recipe.compareTo(o.recipe);
            if (recipeCompare != 0) return recipeCompare;
            if (ItemStack.isSameItemSameComponents(output, o.output)) return 0;
            
            net.minecraft.resources.Identifier thisId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem());
            net.minecraft.resources.Identifier otherId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(o.output.getItem());
            int idCompare = thisId.compareTo(otherId);
            if (idCompare != 0) return idCompare;
            
            return output.getComponents().toString().compareTo(o.output.getComponents().toString());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AssemblyInstruction instruction)) return false;
            return recipe.equals(instruction.recipe) && ItemStack.isSameItemSameComponents(output, instruction.output);
        }

        @Override
        public int hashCode() {
            return recipe.hashCode() * 31 + ItemStack.hashItemAndComponents(output);
        }
    }
}
