/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric;

//? if fabric {
/*import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableSet;

//? if >=1.21.10 {
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
//?} else {
/^import buildcraft.platform.fabric.gametest.ClientGameTestShim.ClientGameTestContext;
import buildcraft.platform.fabric.gametest.ClientGameTestShim.TestServerContext;
^///?}

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.transport.EnumWirePart;
import buildcraft.builders.tile.TileFiller;
import buildcraft.core.marker.volume.LevelSavedDataVolumeBoxes;
import buildcraft.core.marker.volume.VolumeBox;
import buildcraft.core.tile.TileMarkerVolume;
import buildcraft.lib.client.guide.GuiGuide;
import buildcraft.lib.client.guide.GuideManager;
import buildcraft.lib.engine.TileEngineBase_BC8;
import buildcraft.lib.misc.RegistryUtilBC;
import buildcraft.robotics.BCRoboticsPlugs;
import buildcraft.robotics.RobotStationPluggable;
import buildcraft.silicon.BCSiliconItems;
import buildcraft.silicon.BCSiliconPlugs;
import buildcraft.silicon.EnumAssemblyRecipeState;
import buildcraft.silicon.gate.EnumGateLogic;
import buildcraft.silicon.gate.EnumGateMaterial;
import buildcraft.silicon.gate.EnumGateModifier;
import buildcraft.silicon.gate.GateVariant;
import buildcraft.silicon.plug.FacadeBlockStateInfo;
import buildcraft.silicon.plug.FacadeInstance;
import buildcraft.silicon.plug.PluggableFacade;
import buildcraft.silicon.plug.PluggableGate;
import buildcraft.silicon.plug.PluggableLens;
import buildcraft.silicon.plug.PluggableLightSensor;
import buildcraft.silicon.plug.PluggableTimer;
import buildcraft.silicon.tile.TileAssemblyTable;
import buildcraft.silicon.tile.TileLaser;
import buildcraft.transport.BCTransportItems;
import buildcraft.transport.BCTransportPlugs;
import buildcraft.transport.pipe.flow.PipeFlowItems;
import buildcraft.transport.plug.PluggableBlocker;
import buildcraft.transport.plug.PluggablePowerAdaptor;
import buildcraft.transport.tile.TilePipeHolder;

import static buildcraft.platform.fabric.BCFabricClientGameTest.NS;
import static buildcraft.platform.fabric.BCFabricClientGameTest.Y;

/^*
 * The Fabric client game test's second half of shots ({@code BCFabricClientGameTest} calls {@link #run}), there so the
 * visuals can be compared across Minecraft lines: every BuildCraft screen not already shot, a guide entry page, a
 * survival inventory of BuildCraft items and their tooltips, the camera inside oil and inside gaseous fuel, a pipe's and
 * a facade's break particles, the HUD with BuildCraft items, third person in Robot Goggles, a pipe close-up (plugs,
 * facades, lenses, gates, wires, painted pipes, items in flight, dropped pluggable items) and linked markers, a volume
 * box and a laser. Each shot has the same name on every Fabric node.
 * <p>
 * It asserts only that each scene is what it says (the screen opened, the camera is in the fluid, the placed state
 * reached the client); the images are for a person to compare.
 *^/
final class BCFabricVisualScenes {

    /^* The machine screens' row, behind the rig's world shots (which all look north from z 15 or less). *^/
    private static final int SCREEN_ROW_Z = 40;
    private static final int FOG_Z = 55;
    private static final int PIPE_ROW_Z = 70;
    private static final int MARKER_Z = 90;

    private BCFabricVisualScenes() {
    }

    /^* A block placed alone in the screen row, the screen its use opens, and the shot's name. *^/
    private record MachineScreen(String block, String screen, String shot) {}

    private static final List<MachineScreen> MACHINE_SCREENS = List.of(
        new MachineScreen("engine_iron", "ScreenEngineIron", "bc-gui-engine-iron"),
        new MachineScreen("engine_rf", "ScreenEngineFE", "bc-gui-engine-fe"),
        new MachineScreen("mj_dynamo", "ScreenDynamoMJ", "bc-gui-dynamo-mj"),
        new MachineScreen("distiller", "GuiDistiller", "bc-gui-distiller"),
        new MachineScreen("heat_exchange", "GuiHeatExchange", "bc-gui-heat-exchange"),
        new MachineScreen("chute", "GuiChute", "bc-gui-chute"),
        new MachineScreen("autoworkbench_item", "GuiAutoCraftItems", "bc-gui-auto-workbench"),
        new MachineScreen("advanced_crafting_table", "GuiAdvancedCraftingTable", "bc-gui-advanced-crafting-table"),
        new MachineScreen("assembly_table", "GuiAssemblyTable", "bc-gui-assembly-table"),
        new MachineScreen("integration_table", "GuiIntegrationTable", "bc-gui-integration-table"),
        new MachineScreen("programming_table", "GuiProgrammingTable", "bc-gui-programming-table"),
        new MachineScreen("architect", "GuiArchitectTable", "bc-gui-architect-table"),
        new MachineScreen("builder", "GuiBuilder", "bc-gui-builder"),
        new MachineScreen("replacer", "GuiReplacer", "bc-gui-replacer"),
        new MachineScreen("library", "GuiElectronicLibrary", "bc-gui-electronic-library"),
        new MachineScreen("filtered_buffer", "GuiFilteredBuffer", "bc-gui-filtered-buffer"),
        new MachineScreen("requester", "GuiRequester", "bc-gui-requester"),
        new MachineScreen("zone_planner", "GuiZonePlanner", "bc-gui-zone-planner"));

    /^* A lone pipe in the screen row whose use opens its screen. *^/
    private static final List<MachineScreen> PIPE_SCREENS = List.of(
        new MachineScreen("diamond_item", "GuiDiamondPipe", "bc-gui-diamond-pipe"),
        new MachineScreen("diamond_wood_item", "GuiDiamondWoodPipe", "bc-gui-diamond-wood-pipe"),
        new MachineScreen("emzuli_item", "GuiEmzuliPipe", "bc-gui-emzuli-pipe"));

    static void run(ClientGameTestContext ctx, TestServerContext server, Runnable waitForChunks,
            List<String> problems) {
        machineScreens(ctx, server, waitForChunks, problems);
        listScreen(ctx, server, problems);
        guideEntry(ctx, problems);
        inventoryAndTooltips(ctx, server, problems);
        fluidFog(ctx, server, waitForChunks, problems);
        hudAndThirdPerson(ctx, server, waitForChunks, problems);
        pipeCloseUp(ctx, server, waitForChunks, problems);
        particles(ctx, server, waitForChunks, problems);
        markersAndLaser(ctx, server, waitForChunks, problems);
    }

    // ─── Screens ─────────────────────────────────────────────────────────────

    private static void machineScreens(ClientGameTestContext ctx, TestServerContext server, Runnable waitForChunks,
            List<String> problems) {
        int x = -12;
        List<BlockPos> machines = new ArrayList<>();
        for (MachineScreen machine : MACHINE_SCREENS) {
            BlockPos at = new BlockPos(x, Y, SCREEN_ROW_Z);
            if (machine.block().equals("heat_exchange")) {
                // A lone exchanger is no chain and opens nothing: three in a row facing the player (start at the
                // west end), the screen opened from the middle one.
                for (int i = 0; i < 3; i++) {
                    setblock(server, at.east(i), NS + ":heat_exchange[facing=south]");
                }
                at = at.east();
                x += 2;
            } else {
                setblock(server, at, NS + ":" + machine.block());
            }
            machines.add(at);
            x += 3;
        }
        List<BlockPos> pipes = new ArrayList<>();
        for (MachineScreen pipe : PIPE_SCREENS) {
            BlockPos at = new BlockPos(x, Y, SCREEN_ROW_Z);
            BCFabricClientGameTest.pipe(server, at, pipe.block());
            pipes.add(at);
            x += 3;
        }
        // The Filler opens only once it has a box: a volume marker behind it, taken over as a placement would.
        BlockPos filler = new BlockPos(x, Y, SCREEN_ROW_Z);
        BlockPos fillerMarker = filler.north();
        setblock(server, fillerMarker, NS + ":marker_volume");
        setblock(server, filler, NS + ":filler[facing=south]");
        x += 3;
        // The gate, on the south face of a pipe (towards the player).
        BlockPos gatePipe = new BlockPos(x, Y, SCREEN_ROW_Z);
        BCFabricClientGameTest.pipe(server, gatePipe, "cobblestone_item");
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            if (s.overworld().getBlockEntity(gatePipe) instanceof TilePipeHolder holder) {
                holder.replacePluggable(Direction.SOUTH, new PluggableGate(BCSiliconPlugs.gate, holder, Direction.SOUTH,
                    new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER)));
            }
            if (s.overworld().getBlockEntity(filler) instanceof TileFiller tile) {
                tile.onPlacedBy(player(s), ItemStack.EMPTY);
            }
        });
        server.runCommand("tp @p 0.5 " + Y + " " + (SCREEN_ROW_Z + 6.5) + " 180 20");
        ctx.waitTicks(10);
        waitForChunks.run();

        for (int i = 0; i < MACHINE_SCREENS.size(); i++) {
            MachineScreen machine = MACHINE_SCREENS.get(i);
            BCFabricClientGameTest.openScreenOn(ctx, server, machines.get(i), machine.screen(), machine.shot(),
                problems);
        }
        for (int i = 0; i < PIPE_SCREENS.size(); i++) {
            MachineScreen pipe = PIPE_SCREENS.get(i);
            BCFabricClientGameTest.openScreenOn(ctx, server, pipes.get(i), pipe.screen(), pipe.shot(), problems);
        }

        if (awaitClient(ctx, mc -> mc.level.getBlockEntity(filler) instanceof TileFiller tile && tile.hasBox(),
                "the client's Filler never had a box", problems)) {
            BCFabricClientGameTest.openScreenOn(ctx, server, filler, "GuiFiller", "bc-gui-filler", problems);
        }
        if (awaitClient(ctx, mc -> mc.level.getBlockEntity(gatePipe) instanceof TilePipeHolder holder
                && holder.getPluggable(Direction.SOUTH) instanceof PluggableGate,
                "the client never saw the gate on its pipe", problems)) {
            // The gate's own box sits on the pipe's south face, between 12 and 14 sixteenths out.
            openScreenLookingAt(ctx, server, gatePipe,
                new Vec3(gatePipe.getX() + 0.5, gatePipe.getY() + 0.5, gatePipe.getZ() + 0.85), "GuiGate",
                "bc-gui-gate", problems);
        }
    }

    /^* The List, used in the air. *^/
    private static void listScreen(ClientGameTestContext ctx, TestServerContext server, List<String> problems) {
        server.runCommand("item replace entity @p hotbar.7 with " + NS + ":list");
        server.runCommand("tp @p 0.5 " + Y + " " + (SCREEN_ROW_Z + 6.5) + " 180 -30");
        BCFabricClientGameTest.selectSlot(ctx, 7);
        ctx.waitTicks(5);
        ctx.getInput().pressKey(o -> o.keyUse);
        BCFabricClientGameTest.expectScreen(ctx, "GuiList", problems);
        ctx.waitTicks(10);
        BCFabricClientGameTest.clearOverlays(ctx);
        ctx.takeScreenshot("bc-gui-list");
        closeScreen(ctx);
    }

    /^*
     * A guide entry page with a recipe panel: the Stirling engine's, opened on the open book as a link click would.
     *^/
    private static void guideEntry(ClientGameTestContext ctx, List<String> problems) {
        ctx.setScreen(GuiGuide::new);
        ctx.waitTicks(10);
        // A click on the cover opens the book (an animation of a few ticks), as in BCFabricClientGameTest.
        ctx.getInput().setCursorPos(800, 450);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(40);
        String opened = ctx.computeOnClient(mc -> {
            if (!(BCFabricClientGameTest.currentScreen(mc) instanceof GuiGuide gui)) {
                return "no guide open";
            }
            ItemStack engine = item("engine_stone");
            gui.openPage(GuideManager.INSTANCE.getPageFor(engine).createNew(gui));
            return null;
        });
        if (opened != null) {
            problems.add("guide entry: " + opened);
        }
        ctx.waitTicks(20);
        BCFabricClientGameTest.clearOverlays(ctx);
        ctx.takeScreenshot("bc-guide-entry");
        closeScreen(ctx);
    }

    // ─── Inventory, tooltips ─────────────────────────────────────────────────

    /^* The inventory: the grid's first two rows (slots 9 to 26) by code, the hotbar (0 to 7) by command. *^/
    private static void fillInventory(TestServerContext server) {
        String[] commands = {
            "hotbar.0 with " + NS + ":wrench",
            "hotbar.1 with " + NS + ":goggles",
            "hotbar.2 with " + NS + ":gear_diamond",
            "hotbar.3 with " + NS + ":chipset_diamond",
            "hotbar.4 with " + NS + ":robot",
            "hotbar.5 with " + NS + ":robot_station",
            "hotbar.6 with " + NS + ":paintbrush",
            "hotbar.7 with " + NS + ":volume_wrench",
            "hotbar.8 with minecraft:air",
        };
        for (String command : commands) {
            server.runCommand("item replace entity @p " + command);
        }
        server.runOnServer(s -> {
            var inventory = player(s).getInventory();
            inventory.setItem(9, facade(Blocks.STONE.defaultBlockState(), false));
            inventory.setItem(10, facade(redStainedGlass(), false));
            inventory.setItem(11, facade(Blocks.OAK_PLANKS.defaultBlockState(), true));
            inventory.setItem(12, BCSiliconItems.PLUG_GATE.get().getStack(
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER)));
            inventory.setItem(13, BCSiliconItems.PLUG_GATE.get().getStack(
                new GateVariant(EnumGateLogic.OR, EnumGateMaterial.GOLD, EnumGateModifier.DIAMOND)));
            inventory.setItem(14, BCSiliconItems.PLUG_GATE.get().getStack(
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.CLAY_BRICK, EnumGateModifier.NO_MODIFIER)));
            inventory.setItem(15, BCSiliconItems.PLUG_LENS.get().getStack(DyeColor.RED, false));
            inventory.setItem(16, BCSiliconItems.PLUG_LENS.get().getStack(DyeColor.BLUE, true));
            inventory.setItem(17, BCSiliconItems.PLUG_LENS.get().getStack(null, false));
            inventory.setItem(18, item("pipe_wood_item"));
            inventory.setItem(19, item("pipe_diamond_item"));
            inventory.setItem(20, paintedPipe("pipe_stone_item", DyeColor.RED));
            inventory.setItem(21, paintedPipe("pipe_cobble_fluid", DyeColor.BLUE));
            inventory.setItem(22, item("pipe_gold_power"));
            inventory.setItem(23, item("pipe_structure"));
            inventory.setItem(24, new ItemStack(RegistryUtilBC.getValue(BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath(NS, "wire_red")), 16));
            inventory.setItem(25, item("plug_blocker"));
            inventory.setItem(26, item("plug_pulsar"));
        });
    }

    /^* The grid's third row (slots 27 to 35): the fluid containers and tools, by command. *^/
    private static void fillToolsRow(TestServerContext server) {
        String shard = NS + ":fragile_fluid_container[" + NS + ":fluid_content={id:\"" + NS + ":oil\",amount:1000}]";
        String fuelShard = NS + ":fragile_fluid_container[" + NS + ":fluid_content={id:\"" + NS
            + ":fuel_gaseous\",amount:1000}]";
        String[] row = {NS + ":oil_bucket", NS + ":fuel_gaseous_bucket", shard, fuelShard, NS + ":tank",
            NS + ":goggles", NS + ":wrench", NS + ":blueprint_clean", NS + ":template_clean"};
        for (int i = 0; i < row.length; i++) {
            server.runCommand("item replace entity @p inventory." + (18 + i) + " with " + row[i]);
        }
    }

    private static final int SLOT_FACADE = 9;
    private static final int SLOT_GATE = 12;
    private static final int SLOT_OIL_BUCKET = 27;
    private static final int SLOT_SHARD = 29;

    private static void inventoryAndTooltips(ClientGameTestContext ctx, TestServerContext server,
            List<String> problems) {
        fillInventory(server);
        fillToolsRow(server);
        // Survival: in creative the inventory key (and an InventoryScreen) opens the creative screen instead.
        server.runCommand("gamemode survival @p");
        server.runCommand("tp @p 5.5 " + Y + " 9.5 180 45");
        ctx.waitTicks(10);
        BCFabricClientGameTest.setHudHidden(ctx, false);
        ctx.getInput().pressKey(o -> o.keyInventory);
        BCFabricClientGameTest.expectScreen(ctx, "InventoryScreen", problems);
        ctx.waitTicks(10);
        // Park the cursor off the window's slots for the plain shot.
        ctx.getInput().setCursorPos(10, 10);
        ctx.waitTicks(2);
        BCFabricClientGameTest.clearOverlays(ctx);
        ctx.takeScreenshot("bc-inventory-items");

        hoverSlot(ctx, SLOT_SHARD, "fragile_fluid_container", "bc-tooltip-fluid-shard", problems);
        hoverSlot(ctx, SLOT_GATE, "plug_gate", "bc-tooltip-gate", problems);
        hoverSlot(ctx, SLOT_FACADE, "plug_facade", "bc-tooltip-facade", problems);
        hoverSlot(ctx, SLOT_OIL_BUCKET, "oil_bucket", "bc-tooltip-oil-bucket", problems);
        // No filled tank item exists: the tank's block item keeps no fluid (only the fluid shard does).
        BCFabricClientGameTest.LOGGER.info("Tooltip shots: no filled tank item to hover, the tank item holds no fluid");

        ctx.getInput().setCursorPos(10, 10);
        closeScreen(ctx);
        server.runCommand("gamemode creative @p");
        ctx.waitTicks(5);
    }

    /^*
     * Moves the cursor over the inventory slot holding {@code itemId} (inventory index {@code slot}) and screenshots
     * the tooltip; a problem if the screen does not report that slot hovered.
     *^/
    private static void hoverSlot(ClientGameTestContext ctx, int slot, String itemId, String shot,
            List<String> problems) {
        double[] at = ctx.computeOnClient(mc -> {
            if (!(BCFabricClientGameTest.currentScreen(mc) instanceof AbstractContainerScreen<?> screen)) {
                return null;
            }
            for (Slot s : screen.getMenu().slots) {
                if (s.container == mc.player.getInventory() && s.getContainerSlot() == slot) {
                    double scale = mc.getWindow().getGuiScale();
                    int left = intField(screen, "leftPos");
                    int top = intField(screen, "topPos");
                    return new double[] {(left + s.x + 8) * scale, (top + s.y + 8) * scale};
                }
            }
            return null;
        });
        if (at == null) {
            problems.add(shot + ": no inventory screen with slot " + slot);
            return;
        }
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(3);
        String hovered = ctx.computeOnClient(mc -> {
            Screen screen = BCFabricClientGameTest.currentScreen(mc);
            Object slotUnder = screen == null ? null : field(screen, "hoveredSlot");
            if (!(slotUnder instanceof Slot s)) {
                return "nothing";
            }
            return BuiltInRegistries.ITEM.getKey(s.getItem().getItem()).getPath();
        });
        if (!itemId.equals(hovered)) {
            problems.add(shot + ": the cursor is over " + hovered + ", not " + itemId);
        }
        BCFabricClientGameTest.clearOverlays(ctx);
        ctx.takeScreenshot(shot);
    }

    // ─── Fluid fog ───────────────────────────────────────────────────────────

    private static void fluidFog(ClientGameTestContext ctx, TestServerContext server, Runnable waitForChunks,
            List<String> problems) {
        BCFabricClientGameTest.setHudHidden(ctx, true);
        fogPool(ctx, server, waitForChunks, 30, "oil", "bc-fog-oil", problems);
        fogPool(ctx, server, waitForChunks, 38, "fuel_gaseous", "bc-fog-fuel-gaseous", problems);
        BCFabricClientGameTest.setHudHidden(ctx, false);
    }

    /^* A glass box of {@code fluid} source blocks four deep, the player standing in it looking across. *^/
    private static void fogPool(ClientGameTestContext ctx, TestServerContext server, Runnable waitForChunks, int x0,
            String fluid, String shot, List<String> problems) {
        int z0 = FOG_Z;
        server.runCommand("tp @p " + (x0 + 3.5) + " " + (Y + 6) + " " + (z0 + 9.5) + " 180 0");
        ctx.waitTicks(10);
        waitForChunks.run();
        server.runCommand("fill " + x0 + " " + (Y - 1) + " " + z0 + " " + (x0 + 6) + " " + (Y + 4) + " " + (z0 + 6)
            + " minecraft:glass hollow");
        server.runCommand("fill " + (x0 + 1) + " " + Y + " " + (z0 + 1) + " " + (x0 + 5) + " " + (Y + 3) + " "
            + (z0 + 5) + " " + NS + ":" + fluid);
        server.runCommand("tp @p " + (x0 + 3.5) + " " + Y + " " + (z0 + 4.5) + " 180 0");
        ctx.waitTicks(10);
        waitForChunks.run();
        if (awaitClient(ctx, mc -> {
            var at = BlockPos.containing(mc.player.getEyePosition());
            return BuiltInRegistries.FLUID.getKey(mc.level.getFluidState(at).getType()).getNamespace().equals(NS);
        }, shot + ": the camera never entered the " + fluid, problems)) {
            ctx.waitTicks(10);
            BCFabricClientGameTest.clearOverlays(ctx);
            ctx.takeScreenshot(shot);
        }
        server.runCommand("tp @p 5.5 " + Y + " 9.5 180 45");
        ctx.waitTicks(5);
    }

    // ─── HUD, third person ───────────────────────────────────────────────────

    private static void hudAndThirdPerson(ClientGameTestContext ctx, TestServerContext server, Runnable waitForChunks,
            List<String> problems) {
        // The hotbar fillInventory set: wrench, goggles, gear, chipset, robot, docking station, paintbrush, volume
        // wrench. The wrench selected; the HUD on.
        server.runCommand("tp @p 5.5 " + Y + " 9.5 180 45");
        BCFabricClientGameTest.setHudHidden(ctx, false);
        BCFabricClientGameTest.selectSlot(ctx, 0);
        ctx.waitTicks(10);
        waitForChunks.run();
        BCFabricClientGameTest.clearOverlays(ctx);
        BCFabricClientGameTest.checkShot(ctx.takeScreenshot("bc-hud-hotbar"), problems);

        // Third person, front view, wearing the Robot Goggles, the HUD off.
        server.runCommand("item replace entity @p armor.head with " + NS + ":goggles");
        server.runCommand("tp @p 5.5 " + Y + " 9.5 180 10");
        BCFabricClientGameTest.selectSlot(ctx, 8);
        BCFabricClientGameTest.setHudHidden(ctx, true);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        awaitClient(ctx, mc -> mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
            .is(RegistryUtilBC.getValue(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(NS, "goggles"))),
            "the client player never wore the goggles", problems);
        ctx.waitTicks(10);
        BCFabricClientGameTest.clearOverlays(ctx);
        BCFabricClientGameTest.checkShot(ctx.takeScreenshot("bc-third-person-goggles"), problems);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("item replace entity @p armor.head with minecraft:air");
        BCFabricClientGameTest.setHudHidden(ctx, false);
        ctx.waitTicks(2);
    }

    // ─── Pipe close-up ───────────────────────────────────────────────────────

    private static final BlockPos PIPE_START = new BlockPos(0, Y, PIPE_ROW_Z);
    /^* A pipe alone, east of the line, wearing three facades: solid stone, red stained glass, a hollow one. *^/
    private static final BlockPos FACADE_PIPE = new BlockPos(9, Y, PIPE_ROW_Z);
    /^* A bare pipe further east for the particle shot, clear of the dropped items. *^/
    private static final BlockPos PARTICLE_PIPE = new BlockPos(13, Y, PIPE_ROW_Z);

    private static void pipeCloseUp(ClientGameTestContext ctx, TestServerContext server, Runnable waitForChunks,
            List<String> problems) {
        // There first, so the area's chunks are loaded for the commands that build it.
        server.runCommand("tp @p 3.5 " + Y + " " + (PIPE_ROW_Z + 4.0) + " 180 30");
        ctx.waitTicks(10);
        waitForChunks.run();
        String[] line = {"wood_item", "cobblestone_item", "stone_item", "gold_item", "quartz_item", "clay_item"};
        for (int i = 0; i < line.length; i++) {
            BCFabricClientGameTest.pipe(server, PIPE_START.east(i), line[i]);
        }
        setblock(server, PIPE_START.east(line.length), "minecraft:chest");
        BCFabricClientGameTest.pipe(server, FACADE_PIPE, "stone_item");
        BCFabricClientGameTest.pipe(server, PARTICLE_PIPE, "gold_item");
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            TilePipeHolder[] pipes = new TilePipeHolder[line.length];
            for (int i = 0; i < line.length; i++) {
                if (!(level.getBlockEntity(PIPE_START.east(i)) instanceof TilePipeHolder holder)) {
                    throw new AssertionError("No pipe at " + PIPE_START.east(i));
                }
                pipes[i] = holder;
            }
            pipes[2].getPipe().setColour(DyeColor.RED);
            pipes[4].getPipe().setColour(DyeColor.BLUE);
            // Wires first: the pluggables after them each mark the pipe for a network update.
            for (int i = 1; i <= 3; i++) {
                pipes[i].getWireManager().addPart(EnumWirePart.EAST_UP_SOUTH, DyeColor.RED);
                pipes[i].getWireManager().addPart(EnumWirePart.WEST_UP_SOUTH, DyeColor.RED);
                pipes[i].getWireManager().addPart(EnumWirePart.EAST_DOWN_SOUTH, DyeColor.BLUE);
                pipes[i].getWireManager().addPart(EnumWirePart.WEST_DOWN_SOUTH, DyeColor.BLUE);
            }
            place(pipes[0], Direction.UP, h -> new PluggableBlocker(BCTransportPlugs.blocker, h, Direction.UP));
            place(pipes[1], Direction.UP, h -> new PluggableGate(BCSiliconPlugs.gate, h, Direction.UP,
                new GateVariant(EnumGateLogic.OR, EnumGateMaterial.GOLD, EnumGateModifier.DIAMOND)));
            place(pipes[2], Direction.UP, h -> new PluggableLens(BCSiliconPlugs.lens, h, Direction.UP, DyeColor.RED,
                false));
            place(pipes[2], Direction.SOUTH, h -> new PluggableLens(BCSiliconPlugs.lens, h, Direction.SOUTH,
                DyeColor.BLUE, true));
            place(pipes[3], Direction.UP, h -> new RobotStationPluggable(BCRoboticsPlugs.robotStation, h,
                Direction.UP));
            place(pipes[4], Direction.UP, h -> new PluggableTimer(BCSiliconPlugs.timer, h, Direction.UP));
            place(pipes[4], Direction.NORTH, h -> new PluggableFacade(BCSiliconPlugs.facade, h, Direction.NORTH,
                FacadeInstance.createSingle(facadeInfo(Blocks.STONE_BRICKS.defaultBlockState()), true)));
            place(pipes[5], Direction.UP, h -> new PluggableLightSensor(BCSiliconPlugs.lightSensor, h, Direction.UP));
            place(pipes[5], Direction.SOUTH, h -> new PluggablePowerAdaptor(BCTransportPlugs.powerAdaptor, h,
                Direction.SOUTH));

            if (level.getBlockEntity(FACADE_PIPE) instanceof TilePipeHolder holder) {
                place(holder, Direction.SOUTH, h -> new PluggableFacade(BCSiliconPlugs.facade, h, Direction.SOUTH,
                    FacadeInstance.createSingle(facadeInfo(Blocks.STONE.defaultBlockState()), false)));
                place(holder, Direction.WEST, h -> new PluggableFacade(BCSiliconPlugs.facade, h, Direction.WEST,
                    FacadeInstance.createSingle(facadeInfo(redStainedGlass()), false)));
                place(holder, Direction.UP, h -> new PluggableFacade(BCSiliconPlugs.facade, h, Direction.UP,
                    FacadeInstance.createSingle(facadeInfo(Blocks.OAK_PLANKS.defaultBlockState()), true)));
            }

            // Dropped pluggable items on the ground in front of the line, never picked up.
            dropItem(level, new Vec3(1.5, Y + 0.1, PIPE_ROW_Z + 1.6), facade(Blocks.STONE.defaultBlockState(), false));
            dropItem(level, new Vec3(3.0, Y + 0.1, PIPE_ROW_Z + 1.6), BCSiliconItems.PLUG_GATE.get().getStack(
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER)));
            dropItem(level, new Vec3(4.5, Y + 0.1, PIPE_ROW_Z + 1.6),
                facade(redStainedGlass(), false));
        });

        server.runCommand("tp @p 3.5 " + Y + " " + (PIPE_ROW_Z + 4.0) + " 180 30");
        ctx.waitTicks(10);
        waitForChunks.run();
        // Items into the wood pipe from its open west end, once the line has connected.
        try {
            ctx.waitFor(mc -> mc.level.getBlockEntity(PIPE_START) instanceof TilePipeHolder holder
                && holder.getPipe() != null && holder.getPipe().isConnected(Direction.EAST), 200);
        } catch (AssertionError timedOut) {
            problems.add("pipe close-up: the client's wood pipe never connected east");
        }
        server.runOnServer(s -> {
            if (s.overworld().getBlockEntity(PIPE_START) instanceof TilePipeHolder holder
                    && holder.getPipe().getFlow() instanceof PipeFlowItems flow) {
                flow.insertItemsForce(new ItemStack(Items.EMERALD, 3), Direction.WEST, null, 0.02);
                flow.insertItemsForce(new ItemStack(Items.DIAMOND, 1), Direction.WEST, DyeColor.RED, 0.04);
                flow.insertItemsForce(new ItemStack(Items.REDSTONE, 8), Direction.WEST, null, 0.06);
            }
        });
        boolean inFlight = awaitClient(ctx, mc -> {
            for (int i = 0; i < line.length; i++) {
                if (mc.level.getBlockEntity(PIPE_START.east(i)) instanceof TilePipeHolder holder
                        && holder.getPipe() != null && holder.getPipe().getFlow() instanceof PipeFlowItems flow
                        && !flow.getAllItemsForRender().isEmpty()) {
                    return true;
                }
            }
            return false;
        }, "pipe close-up: the client never saw an item travelling in the line", problems);
        awaitClient(ctx, mc -> mc.level.getBlockEntity(PIPE_START.east(1)) instanceof TilePipeHolder holder
            && holder.getPluggable(Direction.UP) instanceof PluggableGate
            && holder.getWireManager().getColorOfPart(EnumWirePart.EAST_UP_SOUTH) == DyeColor.RED
            && mc.level.getBlockEntity(FACADE_PIPE) instanceof TilePipeHolder facades
            && facades.getPluggable(Direction.SOUTH) instanceof PluggableFacade,
            "pipe close-up: the client never saw the gate, the wires or the facades", problems);
        BCFabricClientGameTest.setHudHidden(ctx, true);
        if (inFlight) {
            ctx.waitTicks(5);
        }
        BCFabricClientGameTest.clearOverlays(ctx);
        BCFabricClientGameTest.checkShot(ctx.takeScreenshot("bc-pipe-closeup"), problems);
        // From the south-west, so the stone (south), red glass (west) and hollow oak (top) facades all show.
        server.runCommand("tp @p 7.8 " + Y + " " + (PIPE_ROW_Z + 2.3) + " 215 25");
        ctx.waitTicks(10);
        BCFabricClientGameTest.clearOverlays(ctx);
        BCFabricClientGameTest.checkShot(ctx.takeScreenshot("bc-pipe-facades"), problems);
        BCFabricClientGameTest.setHudHidden(ctx, false);
    }

    // ─── Particles ───────────────────────────────────────────────────────────

    /^* A pipe's and a facade's break (and, from 1.21.10, hit) particles, a tick or two into the effect. *^/
    private static void particles(ClientGameTestContext ctx, TestServerContext server, Runnable waitForChunks,
            List<String> problems) {
        BlockPos pipe = PARTICLE_PIPE;
        BCFabricClientGameTest.setHudHidden(ctx, true);
        server.runCommand("tp @p " + (pipe.getX() + 0.5) + " " + Y + " " + (PIPE_ROW_Z + 2.5) + " 180 35");
        ctx.waitTicks(10);
        waitForChunks.run();
        particleShot(ctx, pipe, "bc-particles-pipe");
        server.runCommand("tp @p " + (FACADE_PIPE.getX() + 0.5) + " " + Y + " " + (PIPE_ROW_Z + 2.5) + " 180 35");
        ctx.waitTicks(10);
        particleShot(ctx, FACADE_PIPE, "bc-particles-facade");
        BCFabricClientGameTest.setHudHidden(ctx, false);
    }

    private static void particleShot(ClientGameTestContext ctx, BlockPos at, String shot) {
        ctx.runOnClient(mc -> {
            BlockState state = mc.level.getBlockState(at);
            mc.level.addDestroyBlockEffect(at, state);
            //? if >=26.3 {
            for (int i = 0; i < 8; i++) {
                mc.level.addBreakingBlockEffects(at, Direction.SOUTH, false);
            }
            //?} elif >=1.21.10 {
            /^for (int i = 0; i < 8; i++) {
                mc.level.addBreakingBlockEffect(at, Direction.SOUTH);
            }
            ^///?} else {
            /^// 1.21.1 has no level hit-effect method (its hook sits in Minecraft.continueAttack): the break alone.
            ^///?}
        });
        ctx.waitTicks(2);
        ctx.takeScreenshot(shot);
    }

    // ─── Markers, volume box, laser ──────────────────────────────────────────

    private static final BlockPos MARKER_CORNER = new BlockPos(0, Y, MARKER_Z);
    private static final BlockPos LASER_TABLE = new BlockPos(16, Y, MARKER_Z + 2);

    private static void markersAndLaser(ClientGameTestContext ctx, TestServerContext server, Runnable waitForChunks,
            List<String> problems) {
        // There first, so the area's chunks are loaded for the commands that build it.
        server.runCommand("tp @p 5.5 " + Y + " " + (MARKER_Z + 10.5) + " 180 30");
        ctx.waitTicks(10);
        waitForChunks.run();
        setblock(server, MARKER_CORNER, NS + ":marker_volume");
        setblock(server, MARKER_CORNER.east(4), NS + ":marker_volume");
        setblock(server, MARKER_CORNER.south(4), NS + ":marker_volume");
        // A free-standing volume box, three by three by four.
        server.runOnServer(s -> {
            LevelSavedDataVolumeBoxes boxes = LevelSavedDataVolumeBoxes.get(s.overworld());
            BlockPos min = new BlockPos(7, Y, MARKER_Z);
            boxes.addVolumeBox(min);
            VolumeBox box = boxes.getVolumeBoxAt(min);
            if (box != null) {
                box.box.setMax(min.offset(3, 2, 3));
                boxes.markDirtyAndBroadcast();
            }
        });
        // The laser: an assembly table holding redstone with its recipes saved, a laser over it facing down, a
        // creative engine (redstone powered) over the laser feeding it.
        setblock(server, LASER_TABLE, NS + ":assembly_table");
        setblock(server, LASER_TABLE.above(2), NS + ":laser[facing=down]");
        setblock(server, LASER_TABLE.above(3), NS + ":engine_creative");
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            BlockState engineState = level.getBlockState(LASER_TABLE.above(3));
            if (level.getBlockEntity(LASER_TABLE.above(3)) instanceof TileEngineBase_BC8 engine
                    && engineState.hasProperty(BuildCraftProperties.BLOCK_FACING_6)) {
                engine.setOrientation(Direction.DOWN);
                level.setBlock(LASER_TABLE.above(3),
                    engineState.setValue(BuildCraftProperties.BLOCK_FACING_6, Direction.DOWN), 3);
            }
            if (level.getBlockEntity(LASER_TABLE) instanceof TileAssemblyTable table) {
                for (int i = 0; i < 4; i++) {
                    table.inv.setStackInSlot(i, new ItemStack(Items.REDSTONE, 64));
                }
            }
        });
        setblock(server, LASER_TABLE.above(4), "minecraft:redstone_block");

        // Link the markers once they are in the server's marker cache (their first level tick).
        boolean linked = false;
        for (int tick = 0; tick < 100 && !linked; tick++) {
            linked = server.computeOnServer(s -> {
                if (!(s.overworld().getBlockEntity(MARKER_CORNER) instanceof TileMarkerVolume marker)) {
                    return false;
                }
                marker.onManualConnectionAttempt(player(s));
                return marker.getCurrentConnection() != null;
            });
            ctx.waitTick();
        }
        if (!linked) {
            problems.add("markers: the volume markers never linked");
        }
        // Save every recipe the table offers once it has listed them, so it asks the laser for power.
        boolean wantsPower = false;
        for (int tick = 0; tick < 100 && !wantsPower; tick++) {
            wantsPower = server.computeOnServer(s -> {
                if (!(s.overworld().getBlockEntity(LASER_TABLE) instanceof TileAssemblyTable table)) {
                    return false;
                }
                table.recipesStates.replaceAll((instruction, state) ->
                    state == EnumAssemblyRecipeState.POSSIBLE ? EnumAssemblyRecipeState.SAVED : state);
                return table.getTarget() > 0;
            });
            ctx.waitTick();
        }
        if (!wantsPower) {
            problems.add("laser: the assembly table never asked for power");
        }

        BCFabricClientGameTest.setHudHidden(ctx, true);
        server.runCommand("item replace entity @p hotbar.7 with " + NS + ":volume_wrench");
        BCFabricClientGameTest.selectSlot(ctx, 7);
        server.runCommand("tp @p 5.5 " + (Y + 2) + " " + (MARKER_Z + 15.5) + " 180 15");
        ctx.runOnClient(mc -> mc.player.getAbilities().flying = true);
        ctx.waitTicks(10);
        waitForChunks.run();
        awaitClient(ctx, mc -> mc.level.getBlockEntity(MARKER_CORNER) instanceof TileMarkerVolume marker
            && marker.getCurrentConnection() != null, "markers: the client never saw the markers linked", problems);
        ctx.waitTicks(5);
        BCFabricClientGameTest.clearOverlays(ctx);
        BCFabricClientGameTest.checkShot(ctx.takeScreenshot("bc-markers-volume-box"), problems);

        server.runCommand("tp @p " + (LASER_TABLE.getX() + 0.5) + " " + (Y + 1) + " " + (LASER_TABLE.getZ() + 4.5)
            + " 180 10");
        ctx.waitTicks(5);
        boolean firing = awaitClient(ctx, mc -> mc.level.getBlockEntity(LASER_TABLE.above(2)) instanceof TileLaser laser
            && laser.isBeamVisible(), "laser: the client's laser never showed its beam", problems, 400);
        if (firing) {
            ctx.waitTicks(2);
        }
        BCFabricClientGameTest.clearOverlays(ctx);
        BCFabricClientGameTest.checkShot(ctx.takeScreenshot("bc-laser-firing"), problems);
        ctx.runOnClient(mc -> mc.player.getAbilities().flying = false);
        BCFabricClientGameTest.setHudHidden(ctx, false);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /^* Stands two and a half blocks south of {@code pos}, looks at {@code target} and uses it with an empty hand. *^/
    private static void openScreenLookingAt(ClientGameTestContext ctx, TestServerContext server, BlockPos pos,
            Vec3 target, String screenName, String shot, List<String> problems) {
        server.runCommand("tp @p " + (pos.getX() + 0.5) + " " + pos.getY() + " " + (pos.getZ() + 2.5) + " 180 30");
        BCFabricClientGameTest.selectSlot(ctx, 8);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, target));
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyUse);
        BCFabricClientGameTest.expectScreen(ctx, screenName, problems);
        ctx.waitTicks(10);
        BCFabricClientGameTest.clearOverlays(ctx);
        ctx.takeScreenshot(shot);
        closeScreen(ctx);
    }

    private static void closeScreen(ClientGameTestContext ctx) {
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    private static boolean awaitClient(ClientGameTestContext ctx, java.util.function.Predicate<Minecraft> condition,
            String failure, List<String> problems) {
        return awaitClient(ctx, condition, failure, problems, 200);
    }

    /^* Waits up to {@code ticks} for {@code condition} on the client; adds {@code failure} and answers false if not. *^/
    private static boolean awaitClient(ClientGameTestContext ctx, java.util.function.Predicate<Minecraft> condition,
            String failure, List<String> problems, int ticks) {
        try {
            ctx.waitFor(mc -> mc.level != null && mc.player != null && condition.test(mc), ticks);
            return true;
        } catch (AssertionError timedOut) {
            problems.add(failure);
            return false;
        }
    }

    private static void setblock(TestServerContext server, BlockPos at, String block) {
        server.runCommand("setblock " + at.getX() + " " + at.getY() + " " + at.getZ() + " " + block);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static ItemStack item(String id) {
        return new ItemStack(RegistryUtilBC.getValue(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(NS, id)));
    }

    private static ItemStack paintedPipe(String id, DyeColor colour) {
        ItemStack stack = item(id);
        stack.set(BCTransportItems.PIPE_COLOUR.get(), colour);
        return stack;
    }

    // By id: 26.3 keeps the stained glass blocks in a colour collection, not one field each.
    private static BlockState redStainedGlass() {
        return RegistryUtilBC.getValue(BuiltInRegistries.BLOCK,
            Identifier.fromNamespaceAndPath("minecraft", "red_stained_glass")).defaultBlockState();
    }

    private static FacadeBlockStateInfo facadeInfo(BlockState state) {
        Block block = state.getBlock();
        return new FacadeBlockStateInfo(state, new ItemStack(block), ImmutableSet.of());
    }

    private static ItemStack facade(BlockState state, boolean hollow) {
        return BCSiliconItems.PLUG_FACADE.get().createItemStack(FacadeInstance.createSingle(facadeInfo(state), hollow));
    }

    private static void place(TilePipeHolder holder, Direction side,
            java.util.function.Function<TilePipeHolder, buildcraft.api.transport.pluggable.PipePluggable> plug) {
        holder.replacePluggable(side, plug.apply(holder));
    }

    private static void dropItem(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, stack, 0, 0, 0);
        entity.setNeverPickUp();
        entity.setUnlimitedLifetime();
        level.addFreshEntity(entity);
    }

    private static int intField(Object owner, String name) {
        Object value = field(owner, name);
        return value instanceof Integer i ? i : 0;
    }

    /^* A field of vanilla's container screen, by its dev-runtime name (the test runs only in the dev workspace). *^/
    private static Object field(Object owner, String name) {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException e) {
                // Up a class.
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return null;
    }
}
*///?}
