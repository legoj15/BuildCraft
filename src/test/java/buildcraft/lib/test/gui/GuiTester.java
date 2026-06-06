package buildcraft.lib.test.gui;

import com.google.gson.Gson;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import buildcraft.lib.net.PacketBufferBC;
import io.netty.buffer.Unpooled;
import buildcraft.lib.gui.ContainerBC_Neptune;
import buildcraft.builders.container.ContainerFiller;
import buildcraft.builders.tile.TileFiller;
import java.io.InputStreamReader;
import java.io.InputStream;

public class GuiTester {

    private static final Gson GSON = new Gson();

    public static void testDynamoUpgrade(GameTestHelper helper) {
        System.out.println(">>> EXECUTING GUI TEST DYNAMO UPGRADE <<<");
        runSpec(helper, "buildcraftunofficial:gui_specs/test_dynamo_upgrade.json");
    }

    public static void testFillerUI(GameTestHelper helper) {
        System.out.println(">>> EXECUTING GUI TEST FILLER UI <<<");
        runSpec(helper, "buildcraftunofficial:gui_specs/test_filler_ui.json");
    }

    public static void runSpec(GameTestHelper helper, String resourcePath) {
        // Since we are running in a GameTest, we can't easily rely on ResourceManagers dynamically in the test environment.
        // The safest way is to load it from the classpath via ClassLoader.
        String path = resourcePath.replace(":", "/");
        InputStream stream = GuiTester.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("Could not find test spec at: " + path);
        }

        GuiTestSpec spec = GSON.fromJson(new InputStreamReader(stream), GuiTestSpec.class);
        
        BlockPos pos = new BlockPos(1, 2, 1);
        
        // 1. Place the block
        //? if >=1.21.10 {
        Item blockItem = BuiltInRegistries.ITEM.get(Identifier.parse(spec.block)).orElseThrow().value();
        //?} else {
        /*Item blockItem = BuiltInRegistries.ITEM.get(Identifier.parse(spec.block));*/
        //?}
        helper.setBlock(pos, net.minecraft.world.level.block.Block.byItem(blockItem));

        helper.runAfterDelay(2, () -> {
            Player mockPlayer = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

            // Execute Actions
            for (GuiTestSpec.GuiAction action : spec.actions) {
                switch (action.type) {
                    case "open_menu":
                        // helper.makeMockPlayer() returns an anonymous GameTestHelper$1 Player
                        // instance, NOT a ServerPlayer, so the old gameMode.useItemOn(...) cast
                        // blew up with a ClassCastException. Bypass the player-interaction path
                        // and instantiate the block's menu directly from its MenuProvider — the
                        // test only cares about menu-level state afterward (slot contents,
                        // container messages), not the click animation.
                        BlockEntity openBe = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
                        if (openBe instanceof net.minecraft.world.MenuProvider menuProvider) {
                            AbstractContainerMenu opened = menuProvider.createMenu(
                                1, mockPlayer.getInventory(), mockPlayer);
                            if (opened != null) {
                                mockPlayer.containerMenu = opened;
                            }
                        }
                        break;
                    case "set_hotbar":
                        //? if >=1.21.10 {
                        Item itemToGive = BuiltInRegistries.ITEM.get(Identifier.parse(action.item)).orElseThrow().value();
                        //?} else {
                        /*Item itemToGive = BuiltInRegistries.ITEM.get(Identifier.parse(action.item));*/
                        //?}
                        ItemStack stack = new ItemStack(itemToGive, action.count != null ? action.count : 1);
                        mockPlayer.getInventory().setItem(action.slot != null ? action.slot : 0, stack);
                        break;
                    case "click_slot":
                        AbstractContainerMenu menu = mockPlayer.containerMenu;
                        if (menu == null) throw new IllegalStateException("No menu open for mock player");
                        // In 1.21.x AbstractContainerMenu.clicked takes a ContainerInput record
                        // instead of the old ClickType enum, which made the pickup path harder
                        // to construct than the previous port was willing to commit. For the
                        // test specs that exist today only "quick_move" (shift-click) is used,
                        // which has a dedicated public entry point — call that directly. If a
                        // regular-click test spec is added later, extend this branch with a
                        // ContainerInput constructor call.
                        if ("quick_move".equals(action.action) && action.slot != null) {
                            menu.quickMoveStack(mockPlayer, action.slot);
                        }
                        break;
                    case "send_container_message":
                        // For Custom Payloads (Buttons, Statements)
                        ContainerBC_Neptune bcContainer = (ContainerBC_Neptune) mockPlayer.containerMenu;
                        PacketBufferBC packetBuffer = new PacketBufferBC(Unpooled.buffer());
                        if ("excavate_toggle".equals(action.string_payload)) {
                            // Empty payload
                        } else if ("set_pattern".equals(action.string_payload)) {
                            // Write Statement wrapper
                            packetBuffer.writeBoolean(true); // Is Not Null
                            packetBuffer.writeUtf(action.pattern_tag);
                            if (action.param_slot_0 != null) {
                                packetBuffer.writeUtf("buildcraftunofficial:item"); // Example statement parameter type
                                packetBuffer.writeUtf(action.param_slot_0);
                            } else {
                                packetBuffer.writeUtf("");
                            }
                            for (int i = 0; i < 3; i++) {
                                packetBuffer.writeUtf(""); // remaining 3 params null
                            }
                        }
                        
                        // Fake a client payload context since we're Server-side Mock Player
                        bcContainer.readMessage(action.message_id, packetBuffer, false, null);
                        packetBuffer.release();
                        break;
                }
            }

            // Execute Asserts
            for (GuiTestSpec.GuiAssert assertion : spec.asserts) {
                if ("slot_equals".equals(assertion.type)) {
                    AbstractContainerMenu menu = mockPlayer.containerMenu;
                    //? if >=1.21.10 {
                    Item expectedItem = BuiltInRegistries.ITEM.get(Identifier.parse(assertion.item)).orElseThrow().value();
                    //?} else {
                    /*Item expectedItem = BuiltInRegistries.ITEM.get(Identifier.parse(assertion.item));*/
                    //?}
                    ItemStack slotItem = menu.getSlot(assertion.slot).getItem();
                    
                    if (!slotItem.is(expectedItem)) {
                        throw new IllegalStateException("Assertion failed: Slot " + assertion.slot + " does not contain " + expectedItem + ". Found: " + slotItem.getItem());
                    }
                    if (assertion.count != null && slotItem.getCount() != assertion.count) {
                        throw new IllegalStateException("Assertion failed: Slot " + assertion.slot + " count is " + slotItem.getCount() + ", expected " + assertion.count);
                    }
                } else if ("filler_excavate_equals".equals(assertion.type)) {
                    ContainerFiller fillerContainer = (ContainerFiller) mockPlayer.containerMenu;
                    if (fillerContainer.getSyncedCanExcavate() != assertion.boolean_value) {
                         throw new IllegalStateException("Filler excavate assertion failed. Expected " + assertion.boolean_value + ", got " + fillerContainer.getSyncedCanExcavate());
                    }
                } else if ("filler_pattern_equals".equals(assertion.type)) {
                    ContainerFiller fillerContainer = (ContainerFiller) mockPlayer.containerMenu;
                    buildcraft.api.filler.IFillerPattern pattern = fillerContainer.getPatternStatement().get();
                    String tag = pattern != null ? pattern.getUniqueTag() : null;
                    if (!actionMatch(tag, assertion.pattern_tag)) {
                        throw new IllegalStateException("Filler pattern assertion failed. Expected " + assertion.pattern_tag + ", got " + tag);
                    }
                }
            }

            helper.succeed();
        });
    }

    private static boolean actionMatch(String s1, String s2) {
        return s1 != null && s1.equals(s2);
    }
}
