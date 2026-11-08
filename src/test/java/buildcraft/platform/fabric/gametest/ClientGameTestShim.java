/*
 * Copyright (c) 2026 the BuildCraftUnofficial contributors
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.platform.fabric.gametest;

//? if fabric && <1.21.10 {
/*import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.CrashReport;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import buildcraft.platform.fabric.gametest.mixin.ClientShimMouseHandlerInvoker;

/^*
 * The part of Fabric API's client game-test API ({@code net.fabricmc.fabric.api.client.gametest.v1}) that
 * {@code BCFabricClientGameTest} uses, for the 1.21.1 node only: its Fabric API (0.116) has no client game-test module
 * (it starts at 1.21.4), so this stands in for it and the one test body runs on every Fabric node. Same names and
 * signatures as Fabric's types, nested here; test sources only (the game-test mod), never the shipped jar.
 * <p>
 * Loom's {@code clientGameTest} run sets {@code -Dfabric.client.gametest} on 1.21.1 too; the game-test mod's initializer
 * calls {@link #install}, which starts the {@code fabric-client-gametest} entrypoints on a test thread at the first
 * client tick without an overlay, as Fabric's runner does, and stops the client when they return (crashes it when one
 * throws, so the run exits non-zero).
 * <p>
 * How it differs from Fabric's runner: the client is not held between test steps. It runs at its own pace and the test
 * thread synchronises on its ticks: {@code waitTick} returns after the next client tick ends, and every
 * {@code runOnClient}/{@code computeOnClient} runs on the client's task queue, before that frame's ticks. A screenshot
 * therefore reads the frame rendered last, which (the test always waits a tick before a shot) shows the state after
 * that tick. Real keyboard and mouse input is cut off and the cursor never grabbed
 * ({@code mixin.ClientShimInputConstantsMixin}), as Fabric's runner does, so a person at the machine cannot disturb
 * the run. The options Fabric sets for consistency are set when the test starts. There is no dedicated server.
 *^/
public final class ClientGameTestShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("Client game test shim");

    /^* Set on Loom's clientGameTest run; the input mixin and {@link #install} do nothing without it. *^/
    public static final boolean ENABLED = System.getProperty("fabric.client.gametest") != null;

    private static final String ENTRYPOINT_KEY = "fabric-client-gametest";
    /^* Fabric's timeout for loading a world and for its chunks to render (SharedConstants.TICKS_PER_MINUTE). *^/
    private static final int TICKS_PER_MINUTE = 1200;

    private static final Object TICK_LOCK = new Object();
    private static long clientTicks;
    private static boolean started;
    private static int screenshotCounter;

    private ClientGameTestShim() {
    }

    /^* From the game-test mod's initializer: on Loom's clientGameTest run, starts the tests once the client is up. *^/
    public static void install() {
        if (!ENABLED || FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (!started && mc.getOverlay() == null) {
                started = true;
                startTestThread();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            synchronized (TICK_LOCK) {
                clientTicks++;
                TICK_LOCK.notifyAll();
            }
        });
    }

    private static void startTestThread() {
        List<FabricClientGameTest> tests =
            FabricLoader.getInstance().getEntrypoints(ENTRYPOINT_KEY, FabricClientGameTest.class);
        Thread thread = new Thread(() -> {
            Minecraft mc = Minecraft.getInstance();
            try {
                ClientGameTestContext context = new ClientGameTestContext();
                context.prepareClient();
                for (FabricClientGameTest test : tests) {
                    test.runTest(context);
                }
                mc.execute(mc::stop);
            } catch (Throwable thrown) {
                // Logged now in case the client cannot report it; the crash makes the run exit non-zero.
                LOGGER.error("A client game test threw", thrown);
                CrashReport report = CrashReport.forThrowable(thrown, "Client game test");
                mc.execute(() -> mc.delayCrash(report));
            }
        }, "Test thread");
        thread.setDaemon(true);
        thread.start();
    }

    /^* Waits for a client or server task, rethrowing what it threw as itself (an assertion stays an assertion). *^/
    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    /^* Fabric's {@code FabricClientGameTest}: the {@code fabric-client-gametest} entrypoint. *^/
    @FunctionalInterface
    public interface FabricClientGameTest {
        void runTest(ClientGameTestContext context);
    }

    /^* Fabric's {@code ClientGameTestContext}, the methods the test uses. Call only from the test thread. *^/
    public static final class ClientGameTestContext {
        private final TestInput input = new TestInput(this);

        private ClientGameTestContext() {
        }

        /^* Fabric's consistency options, and the title screen (a first launch opens the accessibility one). *^/
        private void prepareClient() {
            runOnClient(mc -> {
                Options options = mc.options;
                options.tutorialStep = TutorialSteps.NONE;
                options.cloudStatus().set(CloudStatus.OFF);
                options.onboardAccessibility = false;
                options.renderDistance().set(5);
                options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
                // Fabric cancels the focus events instead; a lost focus must not open the pause menu.
                options.pauseOnLostFocus = false;
                mc.setWindowActive(true);
                if (!(mc.screen instanceof TitleScreen)) {
                    mc.setScreen(new TitleScreen());
                }
            });
            waitTick();
        }

        public void waitTick() {
            waitTicks(1);
        }

        public void waitTicks(int ticks) {
            synchronized (TICK_LOCK) {
                long until = clientTicks + ticks;
                while (clientTicks < until) {
                    try {
                        TICK_LOCK.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("The client game test thread was interrupted", e);
                    }
                }
            }
        }

        /^* Ticks until {@code predicate} holds; an {@link AssertionError} after {@code timeout} ticks, as Fabric. *^/
        public int waitFor(Predicate<Minecraft> predicate, int timeout) {
            for (int i = 0; i < timeout; i++) {
                if (computeOnClient(predicate::test)) {
                    return i;
                }
                waitTick();
            }
            if (!computeOnClient(predicate::test)) {
                throw new AssertionError("Timed out waiting for predicate");
            }
            return timeout;
        }

        public void runOnClient(Consumer<Minecraft> action) {
            computeOnClient(mc -> {
                action.accept(mc);
                return null;
            });
        }

        public <T> T computeOnClient(Function<Minecraft, T> function) {
            Minecraft mc = Minecraft.getInstance();
            return join(mc.submit(() -> function.apply(mc)));
        }

        public void setScreen(Supplier<Screen> screen) {
            runOnClient(mc -> mc.setScreen(screen.get()));
        }

        /^*
         * Saves the frame on screen as {@code screenshots/<counter>_<name>.png} in the game directory, the counter
         * prefix and folder Fabric's runner uses.
         *^/
        public Path takeScreenshot(String name) {
            return computeOnClient(mc -> {
                Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
                Path file = dir.resolve(String.format(Locale.ROOT, "%04d_", screenshotCounter++) + name + ".png");
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    Files.createDirectories(dir);
                    image.writeToFile(file);
                } catch (IOException e) {
                    throw new AssertionError("Cannot write the screenshot " + file, e);
                }
                return file;
            });
        }

        public TestInput getInput() {
            return input;
        }

        public TestWorldBuilder worldBuilder() {
            return new TestWorldBuilder(this);
        }

        /^* Presses the button labelled {@code translationKey} on the open screen, searching nested containers. *^/
        private void clickScreenButton(String translationKey) {
            runOnClient(mc -> {
                String label = Component.translatable(translationKey).getString();
                if (mc.screen == null || !pressButton(mc.screen.children(), label)) {
                    throw new AssertionError("No button '" + translationKey + "' on " + mc.screen);
                }
            });
        }

        private static boolean pressButton(List<? extends GuiEventListener> listeners, String label) {
            for (GuiEventListener listener : listeners) {
                if (listener instanceof Button button && label.equals(button.getMessage().getString())) {
                    button.onPress();
                    return true;
                }
                if (listener instanceof ContainerEventHandler container && pressButton(container.children(), label)) {
                    return true;
                }
            }
            return false;
        }
    }

    /^*
     * Fabric's {@code TestInput}, the methods the test uses: key and button events go through vanilla's own keyboard
     * and mouse handlers, as Fabric's do.
     *^/
    public static final class TestInput {
        private static final Set<Integer> KEYS_DOWN = ConcurrentHashMap.newKeySet();
        private final ClientGameTestContext context;

        private TestInput(ClientGameTestContext context) {
            this.context = context;
        }

        /^* What {@code InputConstants.isKeyDown} answers during the run (the input mixin). *^/
        public static boolean isKeyDown(int keyCode) {
            return KEYS_DOWN.contains(keyCode);
        }

        public void resizeWindow(int width, int height) {
            context.runOnClient(mc -> mc.getWindow().setWindowed(width, height));
        }

        public void pressKey(Function<Options, KeyMapping> keyMapping) {
            InputConstants.Key key = context.computeOnClient(mc -> KeyBindingHelper.getBoundKeyOf(
                keyMapping.apply(mc.options)));
            press(key);
        }

        public void pressMouse(int button) {
            press(InputConstants.Type.MOUSE.getOrCreate(button));
        }

        public void setCursorPos(double x, double y) {
            context.runOnClient(mc -> ((ClientShimMouseHandlerInvoker) mc.mouseHandler)
                .buildcraft$onMove(mc.getWindow().getWindow(), x, y));
        }

        /^* Down, up, then a tick, as Fabric's {@code pressKey}. *^/
        private void press(InputConstants.Key key) {
            send(key, GLFW.GLFW_PRESS);
            send(key, GLFW.GLFW_RELEASE);
            context.waitTick();
        }

        private void send(InputConstants.Key key, int action) {
            if (key.getType() == InputConstants.Type.KEYSYM) {
                if (action == GLFW.GLFW_PRESS) {
                    KEYS_DOWN.add(key.getValue());
                } else {
                    KEYS_DOWN.remove(key.getValue());
                }
            }
            context.runOnClient(mc -> {
                long window = mc.getWindow().getWindow();
                switch (key.getType()) {
                    case KEYSYM -> mc.keyboardHandler.keyPress(window, key.getValue(), 0, action, 0);
                    case SCANCODE -> mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_UNKNOWN, key.getValue(), action, 0);
                    case MOUSE -> ((ClientShimMouseHandlerInvoker) mc.mouseHandler)
                        .buildcraft$onPress(window, key.getValue(), action, 0);
                }
            });
        }
    }

    /^*
     * Fabric's {@code TestWorldBuilder} with its consistent settings: a flat world, seed 1, no structures, and no
     * daylight cycle, weather cycle or mob spawning, made through the create-world screen as Fabric's is.
     *^/
    public static final class TestWorldBuilder {
        private final ClientGameTestContext context;

        private TestWorldBuilder(ClientGameTestContext context) {
            this.context = context;
        }

        public TestSingleplayerContext create() {
            context.runOnClient(mc -> {
                CreateWorldScreen.openFresh(mc, mc.screen);
                if (!(mc.screen instanceof CreateWorldScreen screen)) {
                    throw new AssertionError("CreateWorldScreen.openFresh did not open the create-world screen");
                }
                WorldCreationUiState state = screen.getUiState();
                state.setWorldType(new WorldCreationUiState.WorldTypeEntry(state.getSettings().worldgenLoadContext()
                    .lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)));
                state.setSeed("1");
                state.setGenerateStructures(false);
                GameRules rules = state.getGameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            });
            context.clickScreenButton("selectWorld.create");
            context.waitFor(TestWorldBuilder::worldLoaded, TICKS_PER_MINUTE);
            MinecraftServer server = context.computeOnClient(Minecraft::getSingleplayerServer);
            return new TestSingleplayerContext(context, server);
        }

        private static boolean worldLoaded(Minecraft mc) {
            return mc.level != null && !(mc.screen instanceof ReceivingLevelScreen)
                && !(mc.screen instanceof LevelLoadingScreen);
        }
    }

    /^* Fabric's {@code TestSingleplayerContext}: closing it saves and leaves the world, back to the title screen. *^/
    public static final class TestSingleplayerContext implements AutoCloseable {
        private final ClientGameTestContext context;
        private final TestServerContext server;
        private final TestClientWorldContext clientWorld;

        private TestSingleplayerContext(ClientGameTestContext context, MinecraftServer server) {
            this.context = context;
            this.server = new TestServerContext(server);
            this.clientWorld = new TestClientWorldContext(context);
        }

        public TestServerContext getServer() {
            return server;
        }

        public TestClientWorldContext getClientWorld() {
            return clientWorld;
        }

        @Override
        public void close() {
            context.runOnClient(mc -> {
                if (mc.level == null) {
                    throw new IllegalStateException("Left the world before closing the singleplayer context");
                }
                mc.level.disconnect();
                mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
            });
            context.waitFor(mc -> mc.level == null && mc.getSingleplayerServer() == null, TICKS_PER_MINUTE);
            context.setScreen(TitleScreen::new);
        }
    }

    /^* Fabric's {@code TestServerContext}: commands and code on the integrated server's thread. *^/
    public static final class TestServerContext {
        private final MinecraftServer server;

        private TestServerContext(MinecraftServer server) {
            this.server = server;
        }

        /^* As the server's own command source (permission level 4, at the world spawn), as Fabric's. *^/
        public void runCommand(String command) {
            runOnServer(s -> s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), command));
        }

        public void runOnServer(Consumer<MinecraftServer> action) {
            computeOnServer(s -> {
                action.accept(s);
                return null;
            });
        }

        public <T> T computeOnServer(Function<MinecraftServer, T> function) {
            return join(server.submit(() -> function.apply(server)));
        }
    }

    /^* Fabric's {@code TestClientWorldContext}: waits for the chunks around the player to arrive and be meshed. *^/
    public static final class TestClientWorldContext {
        private final ClientGameTestContext context;

        private TestClientWorldContext(ClientGameTestContext context) {
            this.context = context;
        }

        public int waitForChunksRender() {
            try {
                return context.waitFor(mc -> chunksLoaded(mc) && mc.level.isLightUpdateQueueEmpty()
                    && mc.levelRenderer.hasRenderedAllSections(), TICKS_PER_MINUTE);
            } catch (AssertionError timedOut) {
                throw new AssertionError(context.computeOnClient(mc -> "The chunks did not render within "
                    + TICKS_PER_MINUTE + " ticks: chunks loaded " + chunksLoaded(mc) + ", light queue empty "
                    + (mc.level != null && mc.level.isLightUpdateQueueEmpty()) + ", sections compiled "
                    + mc.levelRenderer.hasRenderedAllSections()), timedOut);
            }
        }

        /^*
         * Every chunk the server sends around the player's own chunk (which a teleport moves at once): those within the
         * render distance by the server's own round view test, as Fabric's check reads the chunks it was sent.
         *^/
        private static boolean chunksLoaded(Minecraft mc) {
            ClientLevel level = mc.level;
            if (level == null || mc.player == null) {
                return false;
            }
            int distance = mc.options.getEffectiveRenderDistance();
            ChunkPos centre = mc.player.chunkPosition();
            for (int z = centre.z - distance; z <= centre.z + distance; z++) {
                for (int x = centre.x - distance; x <= centre.x + distance; x++) {
                    if (ChunkTrackingView.isWithinDistance(centre.x, centre.z, distance, x, z, false)
                        && !level.getChunkSource().hasChunk(x, z)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
*///?}
