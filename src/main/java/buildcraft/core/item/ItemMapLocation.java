/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
//? if >=1.21.10 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import net.minecraft.world.entity.Entity;
//? if >=1.21.10 {
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;


import buildcraft.api.core.IAreaProvider;
import buildcraft.api.core.IBox;
import buildcraft.api.core.IPathProvider;
import buildcraft.api.core.IZone;
import buildcraft.api.items.IMapLocation;

import buildcraft.core.BCCore;

import buildcraft.lib.misc.EntityUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.data.Box;
import buildcraft.robotics.zone.ZoneMapRef;
import buildcraft.robotics.zone.ZoneMapSavedData;
import buildcraft.robotics.zone.ZonePlan;

public class ItemMapLocation extends Item implements IMapLocation {
    private static final String[] STORAGE_TAGS = "x,y,z,side,xMin,xMax,yMin,yMax,zMin,zMax,path,chunkMapping,name".split(",");
    private static final String TAG_MAP_TYPE = "mapType";
    /** Where builds before the map-id model kept a ZONE map's whole plan, inside the stack's custom data. Read only to
     *  migrate such a stack ({@link #migrateLegacyZone}). */
    private static final String TAG_LEGACY_ZONE = "chunkMapping";

    public ItemMapLocation(Item.Properties properties) {
        super(properties);
    }

    // --- MapLocationType helpers using custom data component ---

    private static MapLocationType getTypeFromStack(@Nonnull ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return MapLocationType.CLEAN;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_MAP_TYPE)) {
            return MapLocationType.CLEAN;
        }
        String typeName = NBTUtilBC.getString(tag, TAG_MAP_TYPE, "");
        try {
            return MapLocationType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return MapLocationType.CLEAN;
        }
    }

    private static void setTypeToStack(@Nonnull ItemStack stack, MapLocationType type) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> {
            CompoundTag tag = data.copyTag();
            tag.putString(TAG_MAP_TYPE, type.name());
            return CustomData.of(tag);
        });
    }

    /**
     * Update the CustomModelData component to match the current MapLocationType.
     * CLEAN = 0 (fallback, remove component), SPOT = 1, AREA = 2, PATH = 3, ZONE = 4, PATH_REPEATING = 5.
     */
    private static void updateModelData(@Nonnull ItemStack stack, MapLocationType type) {
        if (type == MapLocationType.CLEAN) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        } else {
            // ordinal: CLEAN=0, SPOT=1, AREA=2, PATH=3, ZONE=4, PATH_REPEATING=5
            //? if >=1.21.10 {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                    java.util.List.of((float) type.ordinal()),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of()
            ));
            //?} else {
            /*// 1.21.1: CustomModelData is the single-int record.
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(type.ordinal()));*/
            //?}
        }
    }

    private static CompoundTag getCustomTag(@Nonnull ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return new CompoundTag();
        }
        return customData.copyTag();
    }

    private static void setCustomTag(@Nonnull ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // --- Tooltip ---

    // Item#appendHoverText is @Deprecated (not for removal) from 1.21.10: Mojang prefers TooltipProvider data
    // components, but vanilla's own items still override it and neither vanilla nor NeoForge offers a per-item
    // successor hook.
    @SuppressWarnings("deprecation")
    @Override
    //? if >=1.21.10 {
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
    //?} else {
    /*// 1.21.1: appendHoverText has no TooltipDisplay and takes List<Component>; adapt to the shared
    // Consumer-based body below via tooltipList::add.
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            List<Component> tooltipList, TooltipFlag flag) {
        Consumer<Component> tooltip = tooltipList::add;
        super.appendHoverText(stack, context, tooltipList, flag);*/
    //?}
        CompoundTag cpt = getCustomTag(stack);

        if (cpt.contains("name")) {
            String name = NBTUtilBC.getString(cpt, "name", "");
            if (!name.isEmpty()) {
                tooltip.accept(Component.literal(name));
            }
        }

        MapLocationType type = getTypeFromStack(stack);
        switch (type) {
            case SPOT: {
                if (cpt.contains("x") && cpt.contains("y") && cpt.contains("z") && cpt.contains("side")) {
                    int x = NBTUtilBC.getInt(cpt, "x", 0);
                    int y = NBTUtilBC.getInt(cpt, "y", 0);
                    int z = NBTUtilBC.getInt(cpt, "z", 0);
                    // A side byte that is not a face (corrupt or hand-edited stack) leaves the face out of the line.
                    Direction side = NBTUtilBC.getDirectionOrNull(cpt, "side", (byte) 0);
                    tooltip.accept(Component.literal("{" + x + ", " + y + ", " + z
                            + (side == null ? "" : ", " + side) + "}"));
                }
                break;
            }
            case AREA: {
                if (cpt.contains("xMin") && cpt.contains("yMin") && cpt.contains("zMin")
                        && cpt.contains("xMax") && cpt.contains("yMax") && cpt.contains("zMax")) {
                    int x = NBTUtilBC.getInt(cpt, "xMin", 0);
                    int y = NBTUtilBC.getInt(cpt, "yMin", 0);
                    int z = NBTUtilBC.getInt(cpt, "zMin", 0);
                    int xLength = NBTUtilBC.getInt(cpt, "xMax", 0) - x + 1;
                    int yLength = NBTUtilBC.getInt(cpt, "yMax", 0) - y + 1;
                    int zLength = NBTUtilBC.getInt(cpt, "zMax", 0) - z + 1;
                    tooltip.accept(Component.literal(
                        "{" + x + ", " + y + ", " + z + "} + {" + xLength + " x " + yLength + " x " + zLength + "}"));
                }
                break;
            }
            case PATH:
            case PATH_REPEATING: {
                ListTag pathNBT = NBTUtilBC.getListOrNull(cpt, "path", Tag.TAG_COMPOUND);
                if (pathNBT != null && pathNBT.size() > 0) {
                    CompoundTag firstTag = NBTUtilBC.getCompoundOrNull(pathNBT, 0);
                    if (firstTag != null) {
                        BlockPos first = NBTUtilBC.readBlockPos(firstTag);
                        tooltip.accept(Component.literal(
                            "{" + first.getX() + ", " + first.getY() + ", " + first.getZ()
                                + "}, (+" + (pathNBT.size() - 1) + " elements)"));
                    }
                }
                break;
            }
            default:
                break;
        }
        if (type != MapLocationType.CLEAN) {
            tooltip.accept(Component.translatable("buildcraft.item.nonclean.usage",
                Component.keybind("key.sneak"), Component.keybind("key.use")));
        }
    }

    // --- Right-click in air (shift to clear) ---

    @Override
    //? if >=1.21.10 {
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return useImpl(level, player, hand);
    }
    //?} else {
    /*// 1.21.1: Item.use returns InteractionResultHolder<ItemStack>. Wrap the shared InteractionResult
    // logic (useImpl) with the held stack — InteractionResult.PASS/SUCCESS are version-neutral.
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return new net.minecraft.world.InteractionResultHolder<>(useImpl(level, player, hand), player.getItemInHand(hand));
    }*/
    //?}

    private InteractionResult useImpl(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return clearMarkerData(stack);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult clearMarkerData(@Nonnull ItemStack stack) {
        if (getTypeFromStack(stack) == MapLocationType.CLEAN) {
            return InteractionResult.PASS;
        }
        CompoundTag nbt = getCustomTag(stack);
        for (String key : STORAGE_TAGS) {
            nbt.remove(key);
        }
        nbt.putString(TAG_MAP_TYPE, MapLocationType.CLEAN.name());
        stack.remove(BCCore.ZONE_MAP_ID.get());
        if (nbt.size() <= 1) {
            // Only the mapType tag remains; clear entirely
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            setCustomTag(stack, nbt);
        }
        updateModelData(stack, MapLocationType.CLEAN);
        return InteractionResult.SUCCESS;
    }

    // --- Right-click on block (record location) ---

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        if (getTypeFromStack(stack) != MapLocationType.CLEAN) {
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();

        ItemStack modified = stack;
        if (stack.getCount() > 1) {
            modified = stack.copy();
            stack.shrink(1);
            modified.setCount(1);
        }

        BlockEntity tile = level.getBlockEntity(pos);
        CompoundTag cpt = getCustomTag(modified);
        MapLocationType newType;

        if (tile instanceof IPathProvider) {
            List<BlockPos> path = ((IPathProvider) tile).getPath();

            if (path.size() > 1 && path.get(0).equals(path.get(path.size() - 1))) {
                newType = MapLocationType.PATH_REPEATING;
            } else {
                newType = MapLocationType.PATH;
            }

            ListTag pathNBT = new ListTag();
            for (BlockPos posInPath : path) {
                pathNBT.add(NBTUtilBC.writeBlockPos(posInPath));
            }
            cpt.put("path", pathNBT);
        } else if (tile instanceof IAreaProvider) {
            newType = MapLocationType.AREA;

            IAreaProvider areaTile = (IAreaProvider) tile;

            cpt.putInt("xMin", areaTile.min().getX());
            cpt.putInt("yMin", areaTile.min().getY());
            cpt.putInt("zMin", areaTile.min().getZ());
            cpt.putInt("xMax", areaTile.max().getX());
            cpt.putInt("yMax", areaTile.max().getY());
            cpt.putInt("zMax", areaTile.max().getZ());
        } else {
            newType = MapLocationType.SPOT;

            cpt.putByte("side", (byte) side.ordinal());
            cpt.putInt("x", pos.getX());
            cpt.putInt("y", pos.getY());
            cpt.putInt("z", pos.getZ());
        }

        cpt.putString(TAG_MAP_TYPE, newType.name());
        setCustomTag(modified, cpt);
        updateModelData(modified, newType);

        if (modified != stack) {
            // We split the stack; give the modified single item back to the player
            if (!player.getInventory().add(modified)) {
                EntityUtil.dropOverflow(player, modified);
            }
        }

        return InteractionResult.SUCCESS;
    }

    // --- IMapLocation implementation ---

    public static IBox getAreaBox(@Nonnull ItemStack item) {
        CompoundTag cpt = getCustomTag(item);
        int xMin = NBTUtilBC.getInt(cpt, "xMin", 0);
        int yMin = NBTUtilBC.getInt(cpt, "yMin", 0);
        int zMin = NBTUtilBC.getInt(cpt, "zMin", 0);
        BlockPos min = new BlockPos(xMin, yMin, zMin);

        int xMax = NBTUtilBC.getInt(cpt, "xMax", 0);
        int yMax = NBTUtilBC.getInt(cpt, "yMax", 0);
        int zMax = NBTUtilBC.getInt(cpt, "zMax", 0);
        BlockPos max = new BlockPos(xMax, yMax, zMax);

        return new Box(min, max);
    }

    public static IBox getPointBox(@Nonnull ItemStack item) {
        CompoundTag cpt = getCustomTag(item);
        MapLocationType type = getTypeFromStack(item);

        switch (type) {
            case SPOT: {
                int x = NBTUtilBC.getInt(cpt, "x", 0);
                int y = NBTUtilBC.getInt(cpt, "y", 0);
                int z = NBTUtilBC.getInt(cpt, "z", 0);
                BlockPos pos = new BlockPos(x, y, z);
                return new Box(pos, pos);
            }
            default:
                return null;
        }
    }

    /** The stored face: DOWN when the stack has no side recorded (whatever its type - the type is not checked), null
     *  when a side byte is recorded but is not a face. */
    @Nullable
    public static Direction getPointFace(@Nonnull ItemStack stack) {
        CompoundTag cpt = getCustomTag(stack);
        return NBTUtilBC.getDirectionOrNull(cpt, "side", (byte) 0);
    }

    @Override
    public IBox getBox(@Nonnull ItemStack item) {
        MapLocationType type = getTypeFromStack(item);

        switch (type) {
            case AREA:
                return getAreaBox(item);
            case SPOT:
                return getPointBox(item);
            default:
                return null;
        }
    }

    @Override
    @Nullable
    public Direction getPointSide(@Nonnull ItemStack item) {
        MapLocationType type = getTypeFromStack(item);

        if (type == MapLocationType.SPOT) {
            CompoundTag cpt = getCustomTag(item);
            return NBTUtilBC.getDirectionOrNull(cpt, "side", (byte) 0);
        }
        return null;
    }

    @Override
    public BlockPos getPoint(@Nonnull ItemStack item) {
        CompoundTag cpt = getCustomTag(item);
        MapLocationType type = getTypeFromStack(item);

        if (type == MapLocationType.SPOT) {
            return new BlockPos(
                NBTUtilBC.getInt(cpt, "x", 0),
                NBTUtilBC.getInt(cpt, "y", 0),
                NBTUtilBC.getInt(cpt, "z", 0)
            );
        }
        return null;
    }

    @Override
    public IZone getZone(@Nonnull ItemStack item) {
        MapLocationType type = getTypeFromStack(item);
        switch (type) {
            case ZONE:
                return readZone(item);
            case AREA:
                return getBox(item);
            case PATH:
            case PATH_REPEATING:
                return getPointBox(item);
            default:
                return null;
        }
    }

    /**
     * The zone a ZONE map names, as a copy the caller may keep or edit. The map carries a {@link ZoneMapRef}
     * ({@link BCCore#ZONE_MAP_ID}) and the zone lives in {@link ZoneMapSavedData}; a stack written by an earlier build
     * still carries the whole plan and is migrated here first ({@link #migrateLegacyZone}).
     *
     * <p>A map naming nothing (no ref and no plan: crafted data or a ZONE map an earlier build wrote empty), or a ref
     * the store does not hold (another world's map), is a zone that contains nothing. It must not read as "no zone": a
     * robot with no work zone works anywhere, an empty zone keeps it idle. The zone samplers read the empty plan's null
     * sample as "nothing to find". The store is the server's, so this is a server-thread read; off it (a client thread
     * beside an integrated server) a migrated map reads as an empty zone and a legacy one as the plan it carries.
     */
    private static ZonePlan readZone(@Nonnull ItemStack stack) {
        ZonePlan legacy = migrateLegacyZone(stack);
        if (legacy != null) {
            return legacy;
        }
        ZoneMapRef ref = stack.get(BCCore.ZONE_MAP_ID.get());
        ZoneMapSavedData store = ref == null ? null : ZoneMapSavedData.forThisThread();
        ZonePlan plan = store == null ? null : store.get(ref);
        return plan != null ? plan : new ZonePlan();
    }

    /** {@link #migrateLegacyZone(ItemStack, ZoneMapSavedData)} against the store this thread may use. */
    @Nullable
    static ZonePlan migrateLegacyZone(@Nonnull ItemStack stack) {
        return migrateLegacyZone(stack, ZoneMapSavedData.forThisThread());
    }

    /**
     * Moves the plan a legacy ZONE map carries in its custom data into {@code store} and points the stack at it,
     * keeping everything else the stack carries. Returns the plan it found when the stack's zone is that plan (null for
     * a stack with no plan, which is left alone, or one whose ref the store already holds, which reads by ref).
     *
     * <p>The stack keeps its own copy of the plan until the store's entry is {@linkplain ZoneMapSavedData#isDurable
     * durable} (encoded for a level save): player data is saved before level data, so dropping it sooner could leave a
     * crash with a map whose zone is in neither file. A later read or inventory tick drops it. A stack whose ref the
     * store does not hold (from another world) takes a ref to its plan here. With no store (off the server thread) the
     * stack is not touched, and the plan it carries is returned.
     */
    @Nullable
    static ZonePlan migrateLegacyZone(@Nonnull ItemStack stack, @Nullable ZoneMapSavedData store) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG_LEGACY_ZONE)) {
            return null;
        }
        CompoundTag cpt = data.copyTag();
        ZoneMapRef ref = stack.get(BCCore.ZONE_MAP_ID.get());
        ZonePlan found = null;
        if (store == null || ref == null || !store.holds(ref)) {
            found = new ZonePlan();
            found.readFromNBT(cpt);
            if (store == null) {
                return found;
            }
            ref = store.intern(found);
            stack.set(BCCore.ZONE_MAP_ID.get(), ref);
        }
        if (store.isDurable(ref)) {
            cpt.remove(TAG_LEGACY_ZONE);
            setCustomTag(stack, cpt);
        }
        return found;
    }

    /**
     * Stamps a {@link ZonePlan} onto a map-location stack: stores a copy of the plan in {@link ZoneMapSavedData},
     * points the stack at it ({@link BCCore#ZONE_MAP_ID}), flips the map type to {@link MapLocationType#ZONE}, and
     * updates the model selector. The inverse of {@link #getZone}'s ZONE branch; used by the Zone Planner's output
     * slot. A stack that already named a zone is pointed at the new one, so copies of it keep theirs (vanilla's
     * re-write fork).
     *
     * <p>A server-thread write. Off it, where the store is not reachable, the plan is written into the stack the way
     * earlier builds did, to be migrated on the first server-side read.
     */
    public static void setZone(@Nonnull ItemStack stack, ZonePlan plan) {
        CompoundTag cpt = getCustomTag(stack);
        ZoneMapSavedData store = ZoneMapSavedData.forThisThread();
        if (store == null) {
            plan.writeToNBT(cpt);
            stack.remove(BCCore.ZONE_MAP_ID.get());
        } else {
            cpt.remove(TAG_LEGACY_ZONE);
            stack.set(BCCore.ZONE_MAP_ID.get(), store.intern(plan));
        }
        cpt.putString(TAG_MAP_TYPE, MapLocationType.ZONE.name());
        setCustomTag(stack, cpt);
        updateModelData(stack, MapLocationType.ZONE);
    }

    // --- Legacy migration of carried items ---

    // Moves a legacy ZONE map's plan off the stack as soon as it sits in an inventory on the server, so the stack
    // stops carrying (and a creative clone stops copying) the whole plan before anything reads its zone.
    @Override
    //? if >=1.21.10 {
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, owner, slot);
        migrateLegacyZone(stack);
    }
    //?} else {
    /*// 1.21.1: inventoryTick runs on both sides and takes a Level, a slot index and a selected flag.
    public void inventoryTick(ItemStack stack, Level level, Entity owner, int slot, boolean selected) {
        super.inventoryTick(stack, level, owner, slot, selected);
        if (!level.isClientSide()) {
            migrateLegacyZone(stack);
        }
    }*/
    //?}

    @Override
    public List<BlockPos> getPath(@Nonnull ItemStack item) {
        CompoundTag cpt = getCustomTag(item);
        MapLocationType type = getTypeFromStack(item);
        switch (type) {
            case PATH:
            case PATH_REPEATING: {
                List<BlockPos> indexList = new ArrayList<>();
                ListTag pathNBT = NBTUtilBC.getListOrNull(cpt, "path", Tag.TAG_COMPOUND);
                if (pathNBT != null) {
                    for (int i = 0; i < pathNBT.size(); i++) {
                        CompoundTag posTag = NBTUtilBC.getCompoundOrNull(pathNBT, i);
                        if (posTag != null) {
                            indexList.add(NBTUtilBC.readBlockPos(posTag));
                        }
                    }
                }
                return indexList;
            }
            case SPOT: {
                List<BlockPos> indexList = new ArrayList<>();
                indexList.add(new BlockPos(
                    NBTUtilBC.getInt(cpt, "x", 0),
                    NBTUtilBC.getInt(cpt, "y", 0),
                    NBTUtilBC.getInt(cpt, "z", 0)
                ));
                return indexList;
            }
            default:
                return null;
        }
    }

    // --- INamedItem ---
    // Note: We can't use 'getName' because Item.getName(ItemStack) returns Component in 1.21.
    // The INamedItem interface methods are implemented here but the getter is renamed
    // to getLocationName to avoid the conflict.

    @Override
    public String getLocationName(@Nonnull ItemStack item) {
        CompoundTag cpt = getCustomTag(item);
        return NBTUtilBC.getString(cpt, "name", "");
    }

    @Override
    public boolean setLocationName(@Nonnull ItemStack item, String name) {
        CompoundTag cpt = getCustomTag(item);
        cpt.putString("name", name);
        setCustomTag(item, cpt);
        return true;
    }
}
