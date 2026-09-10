/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=1.21.10 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}

import net.neoforged.neoforge.common.NeoForge;

import buildcraft.api.boards.RedstoneBoardNBT;
import buildcraft.api.boards.RedstoneBoardRegistry;
import buildcraft.api.boards.RedstoneBoardRobotNBT;
import buildcraft.api.core.NbtApiUtil;
import buildcraft.api.events.RobotEvent;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.robots.IRobotRegistry;
import buildcraft.api.robots.RobotManager;
import buildcraft.api.transport.pipe.IPipeHolder;

import buildcraft.robotics.BCRoboticsItems;
import buildcraft.robotics.boards.BoardRobotEmptyNBT;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.robotics.RobotUtils;
import buildcraft.robotics.entity.EntityRobot;

/**
 * The robot item. Ported from 7.1.x {@code buildcraft.robotics.ItemRobot}.
 *
 * <p>Board and charge ride in {@code DataComponents.CUSTOM_DATA} as {@code {board:{id:String}, energy:long}};
 * a bare stack with no blob at all is treated as an empty-board robot at zero charge, so a
 * {@code /give}n or creative-tab stack always behaves. Every read of that blob must go through
 * {@code NbtApiUtil} — the plain {@code CompoundTag} getters return {@code Optional}s from 1.21.10 onward and
 * raw values before that.
 *
 * <p>Unlike 7.1.x this item does <em>not</em> refuse to place an empty-board robot: Ph3 ships only the empty
 * board, so that guard would make the item unplaceable and there would be nothing to test. An empty-board robot
 * places, docks and idles — that is the Ph3 minimum. Ph4 revisits it once real boards exist. The item also
 * ships recipe-less on purpose: boards arrive in Ph4, and a craftable do-nothing robot would just be an
 * expensive mistake.
 *
 * <p>Also deliberately dropped from 7.1.x: the {@code IEnergyContainerItem} implementation (RF, which main has
 * no equivalent item-level capability for) and the stack-to-16-while-empty nicety (board and charge make two
 * robot stacks almost never interchangeable, so the item is flatly {@code stacksTo(1)}).
 */
// Item.appendHoverText carries Mojang's "override, don't call" @Deprecated marker on >=1.21.10 — the same
// suppression every other BC item with a tooltip carries (see ItemList_BC8).
@SuppressWarnings("deprecation")
public class ItemRobot extends Item {

    /** The CUSTOM_DATA sub-compound holding the board, keyed exactly as 7.1.x did. */
    public static final String TAG_BOARD = "board";

    /** The CUSTOM_DATA key holding stored energy, in micro-MJ. 7.1.x stored an RF int here. */
    public static final String TAG_ENERGY = "energy";

    /** The board sub-compound's own id key. Written by {@link RedstoneBoardNBT#createBoard} and read back by
     *  the board registry, so it is not this item's to rename. */
    private static final String TAG_BOARD_ID = "id";

    public ItemRobot(Item.Properties properties) {
        super(properties);
    }

    // ── The CUSTOM_DATA blob ────────────────────────────────────────────────

    /** Builds a robot stack carrying the given board id and charge.
     *
     * @param boardId The registered board's id (e.g. {@code buildcraftunofficial:empty_robot_board}). A null id
     *            writes no board sub-compound at all, which reads back as the empty board.
     * @param energy Stored energy in micro-MJ, clamped to {@code EntityRobotBase.MAX_POWER} when the robot is
     *            actually placed.
     * @return A single robot stack. */
    public static ItemStack createRobotStack(String boardId, long energy) {
        ItemStack stack = new ItemStack(BCRoboticsItems.ROBOT.get());
        CompoundTag blob = new CompoundTag();
        if (boardId != null && !boardId.isEmpty()) {
            CompoundTag board = new CompoundTag();
            board.putString(TAG_BOARD_ID, boardId);
            blob.put(TAG_BOARD, board);
        }
        blob.putLong(TAG_ENERGY, energy);
        writeBlob(stack, blob);
        return stack;
    }

    /** @return Stored energy in micro-MJ, 0 for a stack with no blob. */
    public static long getEnergy(ItemStack stack) {
        return NbtApiUtil.getLong(readBlob(stack), TAG_ENERGY, 0L);
    }

    /** @return The board id, or null for a stack with no blob — and also for a board sub-compound that exists
     *          but carries no id, which is corrupt data rather than a crash. The caller substitutes the
     *          registry's empty board for null. */
    public static String getBoardId(ItemStack stack) {
        CompoundTag board = NbtApiUtil.getCompound(readBlob(stack), TAG_BOARD);
        String id = NbtApiUtil.getString(board, TAG_BOARD_ID, "");
        return id.isEmpty() ? null : id;
    }

    /** Never null: a stack with no {@code CUSTOM_DATA} yields a fresh empty compound, which every accessor
     *  above then reads its own default out of. */
    private static CompoundTag readBlob(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    /** The documented write incantation, uniform across every node: update {@code CUSTOM_DATA} from its
     *  {@code EMPTY} default rather than assuming the component is already present. */
    private static void writeBlob(ItemStack stack, CompoundTag blob) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, existing -> CustomData.of(blob));
    }

    /** Resolves the stack's board through the registry, falling back to the empty board for an absent or
     *  unregistered id (7.1.x behaviour — {@code ImplRedstoneBoardRegistry.getRedstoneBoard} already returns
     *  the empty board for an unknown id). May still be null if nothing has registered an empty board yet. */
    public static RedstoneBoardRobotNBT getRobotBoard(ItemStack stack) {
        RedstoneBoardRegistry registry = RedstoneBoardRegistry.instance;
        if (registry == null) {
            return null;
        }
        String id = getBoardId(stack);
        RedstoneBoardNBT<?> board = id == null ? registry.getEmptyRobotBoard() : registry.getRedstoneBoard(id);
        return board instanceof RedstoneBoardRobotNBT robotBoard ? robotBoard : registry.getEmptyRobotBoard();
    }

    /** True when the stack is the board-less template: no board sub-compound at all (the bare or corrupt
     *  stack, which {@link #getRobotBoard} also resolves to the empty board), or the empty board's own id.
     *
     *  <p>The template is not a functional robot — there is no program to power — so every charge readout
     *  is suppressed for it: the tooltip line, the inventory bar, and the icon's glowing eyes. 7.1.x hid
     *  its charge line too. Keyed on the id rather than resolved through the registry so the rule stays
     *  put even if a test or addon swaps the empty board out from under {@code setEmptyRobotBoard}. */
    public static boolean hasEmptyBoard(ItemStack stack) {
        String id = getBoardId(stack);
        return id == null || BoardRobotEmptyNBT.ID.equals(id);
    }

    // ── Placement ───────────────────────────────────────────────────────────

    /** Places a robot onto the {@code RobotStationPluggable} on the clicked face.
     *
     * <p>Order is load-bearing and matches 7.1.x: the face has to carry an untaken station, the cancellable
     * {@code RobotEvent.Place} is posted before <em>anything</em> is committed, the id comes from the registry
     * before the entity reaches the world, the robot is positioned at the station's face centre, and the
     * station is taken as MAIN (a plain {@code take} would leave the robot with no linked station and it would
     * shut itself down on its next tick). {@code level.addFreshEntity} is used deliberately rather than
     * {@code EntityType.spawn}, whose signature forks three ways across the nodes. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        BlockEntity tile = level.getBlockEntity(context.getClickedPos());

        if (level.isClientSide()) {
            // The client's copy of a RobotStationPluggable never resolves a DockingStation (onTick is
            // server-only), so it cannot tell a free station from a taken one. All it can honestly answer is
            // "there is a station on that face at all", which is enough to decide whether to swing the arm.
            boolean isStationFace = tile instanceof IPipeHolder holder
                    && holder.getPluggable(face) instanceof RobotStationPluggable;
            return isStationFace ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        DockingStation station = stationOnFace(tile, face);
        if (station == null) {
            return InteractionResult.PASS;
        }
        if (station.isTaken()) {
            // Handled — do NOT fall through to some other use — but nothing is placed and nothing consumed.
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        EntityRobot robot = new EntityRobot(level, getRobotBoard(stack));
        robot.getBattery().setStored(getEnergy(stack));

        RobotEvent.Place event = new RobotEvent.Place(robot, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            // The robot was never added to the level and never handed an id, so there is nothing to unwind —
            // and deliberately no discard() call, which would route a never-registered robot through the
            // registry's kill path.
            return InteractionResult.SUCCESS;
        }

        IRobotRegistry registry = RobotManager.registryProvider.getRegistry(level);
        robot.setUniqueRobotId(registry.getNextRobotId());
        robot.setPos(
                station.getPos().getX() + 0.5 + face.getStepX() * 0.5,
                station.getPos().getY() + 0.5 + face.getStepY() * 0.5,
                station.getPos().getZ() + 0.5 + face.getStepZ() * 0.5);
        station.takeAsMain(robot);
        robot.dock(robot.getLinkedStation());
        level.addFreshEntity(robot);

        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    /** The docking station mounted on {@code face} of {@code tile}, or null. Goes through
     *  {@link RobotUtils#getStations} rather than casting the pluggable directly so a future
     *  station-hosting block entity works the same way. */
    private static DockingStation stationOnFace(BlockEntity tile, Direction face) {
        for (DockingStation candidate : RobotUtils.getStations(tile)) {
            if (candidate.side() == face) {
                return candidate;
            }
        }
        return null;
    }

    // ── Tooltip ─────────────────────────────────────────────────────────────

    @Override
    //? if >=1.21.10 {
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
    //?} else {
    /*// 1.21.1: appendHoverText has no TooltipDisplay and takes List<Component>; adapt to the shared
    // Consumer-based body below via tooltipList::add.
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            java.util.List<Component> tooltipList, TooltipFlag flag) {
        Consumer<Component> tooltip = tooltipList::add;
        super.appendHoverText(stack, context, tooltipList, flag);*/
    //?}
        if (!hasEmptyBoard(stack)) {
            tooltip.accept(chargeLine(getEnergy(stack)));
        }
    }

    /** 7.1.x's charge readout, on honest lang keys. Board robots only — the empty-board template gets
     *  nothing, as in 7.1.x: a blank chassis with no program is not a functional robot, so a charge
     *  readout on it is noise. (Up there the suppression fell out of the empty board being
     *  unplaceable and therefore unable to hold charge; here {@link #hasEmptyBoard} states the rule
     *  directly. The caller gates.) */
    private static Component chargeLine(long energy) {
        long max = EntityRobotBase.MAX_POWER;
        int pct = (int) (energy * 100 / max);
        net.minecraft.network.chat.MutableComponent text;
        if (energy >= max) {
            text = Component.translatable("tip.charge.fullcharge");
        } else if (energy <= 0) {
            text = Component.translatable("tip.charge.nocharge");
        } else {
            text = Component.literal(pct + "% ").append(Component.translatable("tip.charge.charged"));
        }
        return text.withStyle(chargeColour(pct));
    }

    private static ChatFormatting chargeColour(int pct) {
        if (pct >= 80) {
            return ChatFormatting.GREEN;
        } else if (pct >= 50) {
            return ChatFormatting.YELLOW;
        } else if (pct >= 30) {
            return ChatFormatting.GOLD;
        } else if (pct >= 20) {
            return ChatFormatting.RED;
        } else {
            return ChatFormatting.DARK_RED;
        }
    }

    // ── Charge display: the icon's eye LED and the inventory bar ────────────

    /** The charge-ramp colours as literal ARGB, mirroring the {@code ChatFormatting} palette
     *  {@code chargeLine} styles the tooltip with. Kept as literals because the 26.2 line stripped
     *  {@code ChatFormatting} of its RGB data ({@code getColor} is gone there), and a tint int must
     *  be node-stable regardless — the thresholds themselves live only in {@code chargeColour}, which
     *  this maps through. This is the readout ramp (tooltip text and inventory bar), exactly as
     *  7.1.x coloured its charge readout — the eye decal never used it. */
    private static final int TINT_DARK_RED = 0xFFAA0000;
    private static final int TINT_RED = 0xFFFF5555;
    private static final int TINT_GOLD = 0xFFFFAA00;
    private static final int TINT_YELLOW = 0xFFFFFF55;
    private static final int TINT_GREEN = 0xFF55FF55;

    /** The inventory bar's colour for a stored charge, as an opaque ARGB int: the same ramp
     *  {@code chargeLine} styles the tooltip with (dark red empty &rarr; green full), so the bar
     *  and the text always agree. Clamps to {@code [0, MAX_POWER]} first — corrupt overcharged
     *  blobs ramp, not crash. (The eye decal is deliberately NOT this: 7.1.x's eyes were always
     *  red, fading with charge — see {@code chargeEyeTintArgb}.) */
    public static int chargeTintArgb(long energy) {
        long max = EntityRobotBase.MAX_POWER;
        long clamped = Math.max(0L, Math.min(max, energy));
        int pct = (int) (clamped * 100 / max);
        return switch (chargeColour(pct)) {
            case DARK_RED -> TINT_DARK_RED;
            case RED -> TINT_RED;
            case GOLD -> TINT_GOLD;
            case YELLOW -> TINT_YELLOW;
            case GREEN -> TINT_GREEN;
            default -> TINT_DARK_RED;
        };
    }

    /** The eye decal's tint for a stored charge: a neutral (grayscale) multiplier at the charge
     *  fraction. 7.1.x's decal pass drew {@code overlay_side.png} with {@code alpha = storagePercent}
     *  and lighting disabled, so the eyes read as pure red intensity — invisible-dark at 0%, the
     *  decal's own red at full. Item quads bake onto an alpha-cutout sheet with no blending, so the
     *  fade is made by multiplying the red decal toward black instead: same read, same endpoints.
     *  Clamps to {@code [0, MAX_POWER]} first. */
    public static int chargeEyeTintArgb(long energy) {
        long max = EntityRobotBase.MAX_POWER;
        long clamped = Math.max(0L, Math.min(max, energy));
        int v = (int) (255 * clamped / max);
        return 0xFF000000 | v << 16 | v << 8 | v;
    }

    /** The vanilla bar width (0..13 px) for a stored charge: empty shows the bare trough, and only an
     *  exactly-full blob draws all 13 px. */
    public static int chargeBarWidth(long energy) {
        long max = EntityRobotBase.MAX_POWER;
        return (int) (Math.max(0L, Math.min(max, energy)) * 13 / max);
    }

    /** Visible for board robots only, matching the tooltip: a robot that can hold a charge shows the
     *  trough even when empty — the unfilled trough IS the "no charge" state at a glance. The
     *  empty-board template shows nothing at all: no bar, no charge line, and the tint source keeps
     *  its eyes dark ({@link #hasEmptyBoard} is the shared rule). */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !hasEmptyBoard(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return chargeBarWidth(getEnergy(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return chargeTintArgb(getEnergy(stack));
    }
}
