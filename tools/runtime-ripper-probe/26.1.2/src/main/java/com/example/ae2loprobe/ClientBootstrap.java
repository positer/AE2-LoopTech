package com.example.ae2loprobe;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Test-only client: creates a new isolated world and captures native rendered frames. */
@Mod(value = "ae2lo_runtime_probe", dist = Dist.CLIENT)
public final class ClientBootstrap {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String WORLD_PREFIX = "AE2LO-Ripper-Probe-";
    private static final long SEED = 0xAE210004L;
    private static boolean attempted;
    private static int titleTicks;
    private static boolean simulatorHelpRequested;
    private static int simulatorOutputUiStage;
    private static int simulatorOutputUiMask;
    private static final WorldSimulatorExteriorCapture simulatorExterior = new WorldSimulatorExteriorCapture();
    private static int simulatorFinishTicks;
    private static boolean machineChineseApplied;
    private static String pendingScreenshot;
    private static int screenshotDelay;
    private static final java.util.Set<String> CAPTURED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final String UI_SCREENSHOT = "ae2lo-ripper-ui.png";
    private static boolean uiRequested;
    private static int uiDelay = 20;
    private static final long SHUTDOWN_SETTLE_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
    private static boolean shutdownPreparationStarted;
    private static boolean stopRequested;
    private static volatile long savedAtNanos;
    private static volatile int savedAtServerTick;
    private static boolean oldPauseOnLostFocus;
    private static long hiddenClientTicks;
    private static long visibleObservations;
    private static int lastWindowVisible = -1;
    private static boolean hiddenNormalStopObserved;
    private static int oldRenderDistance;
    private static int oldSimulationDistance;
    private static net.minecraft.client.tutorial.TutorialSteps oldTutorialStep;
    private static net.minecraft.client.GraphicsPreset oldGraphicsPreset;

    public ClientBootstrap() {
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::onClientTick);
        if (WorldSimulatorFixture.ENABLED) {
            NeoForge.EVENT_BUS.addListener(ClientBootstrap::onWorldSimulatorFrame);
        }
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> RipperImportFixture.observe());
        LOG.info("AE2LO client probe bootstrap armed; only a brand-new {} world can be created", WORLD_PREFIX);
    }

    private static void onWorldSimulatorFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        simulatorExterior.onFrame(ClientBootstrap::capture);
        var minecraft = Minecraft.getInstance();
        var frame = ProbeState.screenshotRequest;
        if (minecraft.player == null || minecraft.level == null || minecraft.getOverlay() != null) {
            return;
        }
        var screen = minecraft.screen;
        boolean main = isSimulatorScreen(screen);
        boolean sides = main && isSimulatorOverlayOpen(screen);
        var simulator = main && minecraft.player.containerMenu instanceof com.example.ae2lightoptimizer.world.WorldSimulatorMenu menu
                ? menu : null;
        if (simulator != null && !sides && simulator.progress == 0 && simulator.fluidOutput != null
                && !CAPTURED.contains(WorldSimulatorFixture.EMPTY_FRAME)) {
            // Capture the genuine empty track on the first eligible frame rather than risk
            // missing the server's brief startup phase during a slow client join.
            capture(WorldSimulatorFixture.EMPTY_FRAME);
            return;
        }
        if (frame == null || CAPTURED.contains(frame)) return;
        if (!frame.equals(pendingScreenshot)) {
            pendingScreenshot = frame;
            screenshotDelay = 3;
        }
        boolean ready = frame.equals(WorldSimulatorFixture.WORLD_FRAME)
                ? screen == null && minecraft.level.getBlockState(WorldSimulatorFixture.ORIGIN)
                        .is(com.example.ae2lightoptimizer.block.ModBlocks.WORLD_SIMULATOR.get())
                : frame.equals(WorldSimulatorFixture.ACTIVE_ON_FRAME)
                ? simulator != null && !sides && simulator.activeOutput
                : frame.equals(WorldSimulatorFixture.HELP_FRAME)
                ? isSimulatorHelpScreen(screen)
                : frame.equals(WorldSimulatorFixture.SIDES_FRAME) || frame.equals(WorldSimulatorFixture.HOVER_FRAME)
                ? sides
                : simulator != null && !sides && simulator.progress * 4 >= simulator.cycleTicks
                        && simulator.progress * 4 <= 3 * simulator.cycleTicks && simulator.fluidOutput != null;
        if (ready && screenshotDelay > 0 && --screenshotDelay == 0) {
            capture(frame);
        }
    }

    private static boolean isSimulatorScreen(Object screen) {
        return screen != null && screen.getClass().getSimpleName().equals("WorldSimulatorScreen");
    }

    private static boolean isSimulatorHelpScreen(Object screen) {
        return screen != null && screen.getClass().getName().equals("guideme.internal.screen.GuideScreen");
    }

    private static boolean isSimulatorOverlayOpen(Object screen) {
        return Boolean.TRUE.equals(invokeSimulatorNavigation(screen, "isSideConfigurationOpen"));
    }

    private static Object invokeSimulatorNavigation(Object screen, String action) {
        return invokeSimulatorNavigation(screen, action, new Class<?>[0]);
    }

    private static Object invokeSimulatorNavigation(Object screen, String action, Class<?>[] signature, Object... args) {
        try {
            var method = screen.getClass().getDeclaredMethod(action, signature);
            if (!method.trySetAccessible()) throw new IllegalAccessException(action);
            return method.invoke(screen, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot run simulator UI probe: " + action, e);
        }
    }

    private static net.minecraft.client.renderer.Rect2i simulatorFaceBounds(Object screen, appeng.api.orientation.RelativeSide face) {
        return (net.minecraft.client.renderer.Rect2i) invokeSimulatorNavigation(screen, "faceButtonBounds",
                new Class<?>[]{appeng.api.orientation.RelativeSide.class}, face);
    }

    private static void checkSimulatorFaceLayout(Object screen) {
        var faces = appeng.api.orientation.RelativeSide.values();
        var bounds = new java.util.EnumMap<appeng.api.orientation.RelativeSide, net.minecraft.client.renderer.Rect2i>(
                appeng.api.orientation.RelativeSide.class);
        for (var face : faces) bounds.put(face, simulatorFaceBounds(screen, face));
        var top = bounds.get(appeng.api.orientation.RelativeSide.TOP);
        var left = bounds.get(appeng.api.orientation.RelativeSide.LEFT);
        var bottom = bounds.get(appeng.api.orientation.RelativeSide.BOTTOM);
        var right = bounds.get(appeng.api.orientation.RelativeSide.RIGHT);
        var front = bounds.get(appeng.api.orientation.RelativeSide.FRONT);
        var back = bounds.get(appeng.api.orientation.RelativeSide.BACK);
        if (bounds.values().stream().anyMatch(b -> b.getWidth() <= 0 || b.getWidth() != b.getHeight() || b.getWidth() != top.getWidth())
                || !(left.getX() < top.getX() && right.getX() > top.getX()
                && top.getX() == bottom.getX() && top.getX() == front.getX() && right.getX() == back.getX()
                && top.getY() < bottom.getY() && left.getY() == bottom.getY() && right.getY() == bottom.getY()
                && front.getY() == back.getY() && front.getY() > bottom.getY())) {
            throw new AssertionError("Simulator face layout does not match the reference");
        }
        LOG.info("AE2LO simulator native six-face placement matches the reference: {}", bounds);
    }

    private static void simulatorPointer(Minecraft minecraft, double guiX, double guiY) {
        try {
            var window = minecraft.getWindow();
            var move = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
            if (!move.trySetAccessible()) throw new IllegalAccessException("onMove");
            move.invoke(minecraft.mouseHandler, window.handle(),
                    guiX * window.getScreenWidth() / window.getGuiScaledWidth(),
                    guiY * window.getScreenHeight() / window.getGuiScaledHeight());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot position the native client pointer for simulator hover evidence", e);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        verifyHiddenWindow(minecraft);
        if (MachineAudit.ENABLED && !machineChineseApplied && minecraft.getOverlay() == null
                && (minecraft.screen instanceof TitleScreen || minecraft.screen != null
                    && minecraft.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) {
            machineChineseApplied = true;
            minecraft.getLanguageManager().setSelected("zh_cn"); minecraft.options.languageCode = "zh_cn";
            minecraft.reloadResourcePacks(); titleTicks = 0;
            return;
        }
        if (WorldSimulatorFixture.ENABLED && minecraft.getOverlay() == null
                && minecraft.screen instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen warning) {
            String proceed = net.neoforged.fml.i18n.FMLTranslations.parseMessage("fml.button.continue.launch");
            for (var child : warning.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().equals(proceed)) {
                    LOG.info("World simulator fixture acknowledging the NeoForge warning-only startup screen");
                    warning.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
                            button.getX() + 8, button.getY() + 8,
                            new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                    break;
                }
            }
            return;
        }
        if (!attempted && Boolean.getBoolean("ae2lo.probe")
                && minecraft.level == null && minecraft.getSingleplayerServer() == null
                && minecraft.getOverlay() == null && (minecraft.screen instanceof TitleScreen
                || minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) {
            if (++titleTicks >= 40) {
                attempted = true;
                createIsolatedWorld(minecraft);
            }
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        if (!minecraft.getSingleplayerServer().getWorldData().getLevelName().startsWith(WORLD_PREFIX)) return;

        if (WorldSimulatorFixture.ENABLED) {
            var screen = minecraft.screen;
            if (isSimulatorScreen(screen)) {
                boolean open = isSimulatorOverlayOpen(screen);
                if (CAPTURED.contains(WorldSimulatorFixture.MENU_FRAME)
                        && !CAPTURED.contains(WorldSimulatorFixture.SIDES_FRAME) && !open) {
                    invokeSimulatorNavigation(screen, "openSideConfiguration");
                    checkSimulatorFaceLayout(screen);
                    simulatorPointer(minecraft, 2, 2);
                    ProbeState.screenshotRequest = WorldSimulatorFixture.SIDES_FRAME;
                } else if (open && CAPTURED.contains(WorldSimulatorFixture.SIDES_FRAME)
                        && !CAPTURED.contains(WorldSimulatorFixture.HOVER_FRAME)
                        && !WorldSimulatorFixture.HOVER_FRAME.equals(ProbeState.screenshotRequest)) {
                    var back = simulatorFaceBounds(screen, appeng.api.orientation.RelativeSide.BACK);
                    simulatorPointer(minecraft, back.getX() + back.getWidth() / 2.0, back.getY() + 3);
                    ProbeState.screenshotRequest = WorldSimulatorFixture.HOVER_FRAME;
                } else if (open && CAPTURED.contains(WorldSimulatorFixture.HOVER_FRAME)) {
                    invokeSimulatorNavigation(screen, "backToSimulator");
                }
                if (CAPTURED.contains(WorldSimulatorFixture.HOVER_FRAME)
                        && screen instanceof appeng.client.gui.AEBaseScreen<?> gui
                        && minecraft.player.containerMenu instanceof com.example.ae2lightoptimizer.world.WorldSimulatorMenu simulator) {
                    int x = gui.getGuiLeft() - 9;
                    if (simulatorOutputUiStage == 0) {
                        if (simulator.activeOutput) throw new AssertionError("New simulator unexpectedly starts with output on");
                        simulatorOutputUiMask = simulator.exportFaces;
                        int y = gui.getGuiTop() + 34;
                        if (!screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
                            x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false)) throw new AssertionError("Active output control did not receive first native click");
                        simulatorPointer(minecraft, 2, 2);
                        simulatorOutputUiStage = 1;
                        ProbeState.screenshotRequest = WorldSimulatorFixture.ACTIVE_ON_FRAME;
                    } else if (simulatorOutputUiStage == 1 && simulator.activeOutput
                            && CAPTURED.contains(WorldSimulatorFixture.ACTIVE_ON_FRAME)) {
                        if (simulator.exportFaces != simulatorOutputUiMask) throw new AssertionError("Switch altered active face mask");
                        int y = gui.getGuiTop() + 34;
                        if (!screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
                            x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false)) throw new AssertionError("Active output control did not receive second native click");
                        simulatorOutputUiStage = 2;
                    } else if (simulatorOutputUiStage == 2 && !simulator.activeOutput && !simulatorHelpRequested) {
                        if (simulator.exportFaces != simulatorOutputUiMask) throw new AssertionError("Switch altered active face mask");
                        LOG.info("AE2LO native output toolbar: off -> on -> off, face mask unchanged");
                        simulatorHelpRequested = true;
                        int y = gui.getGuiTop() + 12;
                        simulatorPointer(minecraft, x, y);
                        if (!screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
                            x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false)) throw new AssertionError("First simulator toolbar button did not accept the help click");
                        ProbeState.screenshotRequest = WorldSimulatorFixture.HELP_FRAME;
                    }
                }
            } else if (isSimulatorHelpScreen(screen) && CAPTURED.contains(WorldSimulatorFixture.HELP_FRAME)) {
                screen.onClose();
            }
            if (ProbeState.finished) {
                boolean expired = ++simulatorFinishTicks > 340;
                boolean uiCaptured = CAPTURED.contains(WorldSimulatorFixture.MENU_FRAME)
                        && CAPTURED.contains(WorldSimulatorFixture.EMPTY_FRAME)
                        && CAPTURED.contains(WorldSimulatorFixture.SIDES_FRAME)
                        && CAPTURED.contains(WorldSimulatorFixture.HOVER_FRAME)
                        && CAPTURED.contains(WorldSimulatorFixture.HELP_FRAME)
                        && CAPTURED.contains(WorldSimulatorFixture.ACTIVE_ON_FRAME);
                if (uiCaptured && !CAPTURED.contains(WorldSimulatorFixture.WORLD_FRAME)) {
                    if (minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                        minecraft.player.closeContainer();
                    }
                    minecraft.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                            net.minecraft.world.phys.Vec3.atCenterOf(WorldSimulatorFixture.ORIGIN));
                    ProbeState.screenshotRequest = WorldSimulatorFixture.WORLD_FRAME;
                }
                boolean exteriorReady = false;
                if (uiCaptured && CAPTURED.contains(WorldSimulatorFixture.WORLD_FRAME)) {
                    if (minecraft.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                        minecraft.player.closeContainer();
                    }
                    exteriorReady = simulatorExterior.tick(CAPTURED, ClientBootstrap::capture);
                }
                boolean viewersReady = WorldSimulatorViewerAudit.check(evidenceDirectory(minecraft), expired);
                if (viewersReady && (exteriorReady || expired)) {
                    advanceNormalShutdown(minecraft);
                }
            }
            return;
        }

        if (IntegratedCpuFixture.ENABLED) {
            var frame = ProbeState.screenshotRequest;
            if (frame != null && !CAPTURED.contains(frame)) {
                if (!frame.equals(pendingScreenshot)) { pendingScreenshot = frame; screenshotDelay = 20; }
                if (screenshotDelay > 0 && --screenshotDelay == 0) {
                    String requiredScreen = switch (frame) {
                        case IntegratedCpuFixture.CPU_FRAME -> "LoopIntegratedCpuScreen";
                        case IntegratedCpuFixture.DRIVE_FRAME -> "GuiExDrive";
                        case IntegratedCpuFixture.TICKER_FRAME -> "LoopTickerScreen";
                        default -> "";
                    };
                    if (!requiredScreen.isEmpty() && (minecraft.screen == null
                            || !minecraft.screen.getClass().getSimpleName().equals(requiredScreen))) {
                        screenshotDelay = 1;
                        return;
                    }
                    var menu = minecraft.player.containerMenu;
                    if (IntegratedCpuFixture.CPU_FRAME.equals(frame) || IntegratedCpuFixture.DRIVE_FRAME.equals(frame)) {
                        var slots = menu.slots.subList(0, 20).stream().map(slot -> java.util.List.of(slot.x, slot.y)).toList();
                        String file = IntegratedCpuFixture.CPU_FRAME.equals(frame) ? "cpu-menu-slots.json" : "extended-drive-slots.json";
                        try {
                            Files.writeString(evidenceDirectory(minecraft).resolve(file),
                                    new com.google.gson.Gson().toJson(slots));
                        } catch (java.io.IOException error) {
                            throw new IllegalStateException(error);
                        }
                    }
                    capture(frame);
                }
            }
            if (ProbeState.finished) advanceNormalShutdown(minecraft);
            return;
        }
        if (OmniProbe.ENABLED) {
            if (OmniClient.tick(minecraft)) advanceNormalShutdown(minecraft);
            return;
        }
        if (FactoryWirelessProbe.ENABLED) {
            if (ProbeState.finished) advanceNormalShutdown(minecraft);
            return;
        }
        if (LoopTickerPartFixture.ENABLED) {
            if (minecraft.screen instanceof com.example.ae2lightoptimizer.client.LoopTickerScreen
                    && !CAPTURED.contains(LoopTickerPartFixture.MENU_FRAME) && ++uiDelay >= 10) {
                capture(LoopTickerPartFixture.MENU_FRAME);
            }
            if (ProbeState.finished) advanceNormalShutdown(minecraft);
            return;
        }
        if (MachineAudit.ENABLED) {
            MachineAuditClient.tick(minecraft);
            var frame = ProbeState.screenshotRequest;
            if (frame != null && !CAPTURED.contains(frame)) {
                if (!frame.equals(pendingScreenshot)) { pendingScreenshot = frame; screenshotDelay = 15; }
                if (screenshotDelay > 0 && --screenshotDelay == 0) capture(frame);
            }
            if (ProbeState.finished && (frame == null || CAPTURED.contains(frame))) advanceNormalShutdown(minecraft);
            return;
        }
        DynamicPatternClient.observePatternAccess(minecraft);
        var request = ProbeState.screenshotRequest;
        if (request != null && !CAPTURED.contains(request)) {
            if (!request.equals(pendingScreenshot)) {
                pendingScreenshot = request;
                screenshotDelay = 20;
            }
            if (screenshotDelay > 0 && --screenshotDelay == 0) capture(request);
        }
        if (ProbeState.finished && request != null && CAPTURED.contains(request)) {
            if (!uiRequested) {
                uiRequested = true;
                var playerId = minecraft.player.getUUID();
                var server = minecraft.getSingleplayerServer();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    var entity = server.overworld().getBlockEntity(new net.minecraft.core.BlockPos(-2, 100, 0));
                    if (player != null && entity instanceof com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity ripper) {
                        // Native menus enforce distance; keep this test player within the provider's use radius.
                        player.connection.teleport(-4.5, 102, 3.5, -145f, 20f);
                        ripper.openMenu(player, appeng.menu.locator.MenuLocators.forBlockEntity(ripper));
                        LOG.info("AE2LO probe opened the production Crafting Ripper menu for visual validation");
                    } else LOG.error("AE2LO probe could not find its production ripper/player for UI validation");
                });
            }
            if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("CraftingRipperScreen")
                    && !CAPTURED.contains(UI_SCREENSHOT) && uiDelay > 0 && --uiDelay == 0) capture(UI_SCREENSHOT);
            if (CAPTURED.contains(UI_SCREENSHOT) && !DynamicPatternClient.tick(minecraft)) return;
            if (CAPTURED.contains(UI_SCREENSHOT) && !RipperImportClient.tick(minecraft)) return;
            if (CAPTURED.contains(UI_SCREENSHOT) && Boolean.getBoolean("ae2lo.probe.autoExit")) {
                advanceNormalShutdown(minecraft);
            }
        }
    }

    /** Observe every real client tick; the launcher agent, not this observer, hides the window. */
    private static void verifyHiddenWindow(Minecraft minecraft) {
        if (!Boolean.getBoolean("ae2lo.probe") || !Boolean.getBoolean("ae2lo.probe.background")) return;
        hiddenClientTicks++;
        lastWindowVisible = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(
                minecraft.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_VISIBLE);
        if (lastWindowVisible != 0) visibleObservations++;
        if (hiddenClientTicks == 1 || hiddenClientTicks % 100 == 0 || lastWindowVisible != 0) {
            writeHiddenWindowState(minecraft);
        }
        if (lastWindowVisible != 0) {
            throw new IllegalStateException("Background ripper probe window became visible at client tick " + hiddenClientTicks);
        }
    }

    private static void writeHiddenWindowState(Minecraft minecraft) {
        if (!Boolean.getBoolean("ae2lo.probe.background")) return;
        var state = new java.util.LinkedHashMap<String, Object>();
        state.put("observedAt", java.time.Instant.now().toString());
        state.put("clientTicksChecked", hiddenClientTicks);
        state.put("visibleObservations", visibleObservations);
        state.put("lastWindowVisible", lastWindowVisible);
        state.put("normalStopObserved", hiddenNormalStopObserved);
        state.put("phase", ProbeState.phase);
        state.put("world", minecraft.getSingleplayerServer() == null ? "" :
                minecraft.getSingleplayerServer().getWorldData().getLevelName());
        state.put("productionCodeSource", String.valueOf(
                com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity.class
                        .getProtectionDomain().getCodeSource().getLocation()));
        state.put("probeCodeSource", String.valueOf(ClientBootstrap.class.getProtectionDomain().getCodeSource().getLocation()));
        try {
            Files.writeString(evidenceDirectory(minecraft).resolve("hidden-window-state.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(state));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot persist hidden-window evidence", error);
        }
    }

    /** Timing experiment for vanilla chunk unload shutdown; the server keeps ticking throughout. */
    private static void advanceNormalShutdown(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || stopRequested) return;
        if (!shutdownPreparationStarted) {
            shutdownPreparationStarted = true;
            shutdownEvent(minecraft, "prepare_requested", "world=" + server.getWorldData().getLevelName());
            server.execute(() -> {
                if (!server.getWorldData().getLevelName().startsWith(WORLD_PREFIX)) {
                    shutdownEvent(minecraft, "prepare_refused", "Integrated world changed; auto-exit remains disabled");
                    return;
                }
                try {
                    var level = server.overworld();
                    // These are exactly the nine temporary tickets installed by RuntimeRipperProbe.start.
                    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                        boolean removed = level.setChunkForced(x, z, false);
                        shutdownEvent(minecraft, "ticket_released", "chunk=" + x + "," + z + " removed=" + removed);
                    }
                    shutdownEvent(minecraft, "save_begin", "serverTick=" + server.getTickCount());
                    long began = System.nanoTime();
                    boolean saved = server.saveEverything(false, true, true);
                    shutdownEvent(minecraft, "save_complete", "saved=" + saved + " elapsedMillis="
                            + (System.nanoTime() - began) / 1_000_000 + " serverTick=" + server.getTickCount());
                    savedAtServerTick = server.getTickCount();
                    savedAtNanos = System.nanoTime();
                    shutdownEvent(minecraft, "settling", "Keep normal client/server ticking for 25 seconds after pre-save");
                } catch (Throwable error) {
                    LOG.error("AE2LO probe pre-save failed; leaving client open without forced exit", error);
                    shutdownEvent(minecraft, "prepare_failed", error.toString());
                }
            });
            return;
        }
        long completedAt = savedAtNanos;
        if (completedAt == 0 || System.nanoTime() - completedAt < SHUTDOWN_SETTLE_NANOS) return;
        stopRequested = true;
        shutdownEvent(minecraft, "settled", "elapsedMillis=" + (System.nanoTime() - completedAt) / 1_000_000
                + " serverTicks=" + (server.getTickCount() - savedAtServerTick));
        shutdownEvent(minecraft, "disconnect_begin", "Keep renderDistance=" + minecraft.options.renderDistance().get()
                + " simulationDistance=" + minecraft.options.simulationDistance().get()
                + "; native disconnect waits for integrated server shutdown");
        minecraft.disconnect(new TitleScreen(), false);
        shutdownEvent(minecraft, "disconnect_complete", "serverShutdown=" + server.isShutdown()
                + " integratedServerPresent=" + (minecraft.getSingleplayerServer() != null));
        if (!server.isShutdown() || minecraft.getSingleplayerServer() != null) {
            shutdownEvent(minecraft, "disconnect_incomplete", "Do not restore distances or force process exit while server remains alive");
            return;
        }
        minecraft.options.graphicsPreset().set(oldGraphicsPreset);
        minecraft.options.renderDistance().set(oldRenderDistance);
        minecraft.options.simulationDistance().set(oldSimulationDistance);
        minecraft.getTutorial().stop();
        minecraft.options.tutorialStep = oldTutorialStep;
        shutdownEvent(minecraft, "tutorial_restored", "step=" + oldTutorialStep + "; integrated server already disconnected");
        minecraft.options.pauseOnLostFocus = oldPauseOnLostFocus;
        shutdownEvent(minecraft, "options_restored", "renderDistance=" + oldRenderDistance
                + " simulationDistance=" + oldSimulationDistance + " pauseOnLostFocus=" + oldPauseOnLostFocus
                + "; helper does not call Options.save");
        shutdownEvent(minecraft, "normal_stop_requested", "Calling Minecraft.stop after native integrated-server disconnect completed");
        hiddenNormalStopObserved = true;
        writeHiddenWindowState(minecraft);
        minecraft.stop();
    }

    private static synchronized void shutdownEvent(Minecraft minecraft, String phase, String detail) {
        String line = java.time.Instant.now() + " " + phase + " " + detail;
        LOG.info("AE2LO probe shutdown experiment: {}", line);
        try {
            Files.writeString(evidenceDirectory(minecraft).resolve("client-shutdown.log"), line + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException error) {
            LOG.error("Cannot persist client shutdown evidence", error);
        }
    }

    private static void createIsolatedWorld(Minecraft minecraft) {
        String defaultName = WORLD_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        String worldId = System.getProperty("ae2lo.probe.world", defaultName);
        if (!worldId.matches("AE2LO-Ripper-Probe-[A-Za-z0-9_.-]+")) {
            LOG.error("Refusing unsafe/non-probe world id {}", worldId);
            return;
        }
        var saves = minecraft.gameDirectory.toPath().toAbsolutePath().normalize().resolve("saves");
        var target = saves.resolve(worldId).normalize();
        boolean migrationCopy = Boolean.getBoolean("ae2lo.probe.migrationCopy")
                && Files.isRegularFile(target.resolve(".ae2lpt-migration-fixture"));
        if (!target.getParent().equals(saves) || (Files.exists(target) && !migrationCopy)) {
            LOG.error("Refusing existing or non-isolated world path {}", target);
            return;
        }
        // In-memory only: the harness must continue ticking while the evidence collector reads logs.
        // No Options.save call is made by this test helper.
        oldTutorialStep = minecraft.options.tutorialStep;
        minecraft.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        minecraft.getTutorial().stop();
        shutdownEvent(minecraft, "tutorial_suspended", "step=" + oldTutorialStep + "->NONE; in-memory only, no Tutorial.setStep or Options.save");
        oldPauseOnLostFocus = minecraft.options.pauseOnLostFocus;
        oldGraphicsPreset = minecraft.options.graphicsPreset().get();
        oldRenderDistance = minecraft.options.renderDistance().get();
        oldSimulationDistance = minecraft.options.simulationDistance().get();
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.renderDistance().set(6);
        minecraft.options.simulationDistance().set(5);
        shutdownEvent(minecraft, "options_overridden", "renderDistance=" + oldRenderDistance + "->6 simulationDistance="
                + oldSimulationDistance + "->5 pauseOnLostFocus=" + oldPauseOnLostFocus + "->false; in-memory only");
        if (migrationCopy) {
            minecraft.createWorldOpenFlows().openWorld(worldId,
                    () -> LOG.error("Loading the isolated migration fixture was cancelled"));
            return;
        }
        var settings = new LevelSettings(worldId, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        LOG.info("AE2LO client probe creating NEW world {} at {} with seed {}", worldId, target, SEED);
        minecraft.createWorldOpenFlows().createFreshLevel(worldId, settings,
                new WorldOptions(SEED, false, false), WorldPresets::createNormalWorldDimensions, minecraft.screen);
    }

    /** Safe from the server thread; actual framebuffer access is dispatched to the client/render thread. */
    public static void capture(String fileName) {
        if (fileName == null || !fileName.matches("ae2lo-ripper-[A-Za-z0-9_.-]+\\.png")) {
            throw new IllegalArgumentException("Unexpected probe screenshot name: " + fileName);
        }
        var minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            LOG.info("AE2LO native frame capture requested: {} phase={} screen={}", fileName, ProbeState.phase,
                    minecraft.screen == null ? "world" : minecraft.screen.getClass().getName());
            Screenshot.grab(evidenceDirectory(minecraft).toFile(), fileName, minecraft.getMainRenderTarget(), 1,
                    message -> onScreenshotComplete(minecraft, fileName, message));
        });
    }

    private static java.nio.file.Path evidenceDirectory(Minecraft minecraft) {
        var path = java.nio.file.Path.of(System.getProperty("ae2lo.probe.reportDir",
                System.getProperty("ae2lo.probe.output", minecraft.gameDirectory.getAbsolutePath())))
                .toAbsolutePath().normalize();
        try { Files.createDirectories(path); }
        catch (java.io.IOException error) { throw new IllegalStateException("Cannot create probe evidence directory", error); }
        return path;
    }

    private static void onScreenshotComplete(Minecraft minecraft, String fileName, net.minecraft.network.chat.Component message) {
        var path = evidenceDirectory(minecraft).resolve("screenshots").resolve(fileName);
        if (Files.isRegularFile(path)) {
            CAPTURED.add(fileName);
            writeHiddenWindowState(minecraft);
            if (!UI_SCREENSHOT.equals(fileName)) ProbeState.screenshotCompleted = fileName;
            LOG.info("AE2LO native frame saved {}: {}", path.toAbsolutePath(), message.getString());
        } else {
            LOG.error("AE2LO native frame capture failed {}: {}", path.toAbsolutePath(), message.getString());
        }
    }
}
