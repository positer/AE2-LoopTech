package com.example.ae2lightoptimizer.client;

import appeng.client.api.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import appeng.api.orientation.RelativeSide;
import net.minecraft.client.renderer.Rect2i;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.ProgressBar;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;
import appeng.util.Icon;
import com.example.ae2lightoptimizer.world.WorldSimulatorMenu;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class WorldSimulatorScreen extends AEBaseScreen<WorldSimulatorMenu> {
    private static final String LABEL = "gui.ae2lpt.world_simulator.";
    private static final int INPUT_X = 8, OUTPUT_X = 155, TANK_Y = 30;
    private static final int TANK_WIDTH = 18, TANK_HEIGHT = 54;
    private static final int RECESS_SHADOW = 0xff858a9a;
    private static final int RECESS_LIGHT = 0xffeceef4;
    private static final int ITEM_WELL = 0xffa9aebe;
    private static final int FLUID_WELL = 0xff777e8e;

    private final WorldSimulatorSidesScreen sides;
    private int pendingClear = -1;
    private long pendingClearTime;

    WorldSimulatorScreen(WorldSimulatorMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        Blitter progressTrack = style.getImage("progressTrack").copy();
        widgets.add("progressBar", new ProgressBar(menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL) {
            @Override
            public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                progressTrack.dest(getX(), getY()).blit(graphics);
                super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            }
        });
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE),
                () -> Upgrades.getTooltipLinesForMachine(menu.upgrades().getUpgradableItem())));
        addToLeftToolbar(new ModeButton(() -> menu.activeOutput ? Icon.AUTO_EXPORT_ON : Icon.AUTO_EXPORT_OFF,
                () -> List.of(tr("active_output"), tr(menu.activeOutput ? "active_output.on" : "active_output.off")),
                () -> send(WorldSimulatorMenu.TOGGLE_ACTIVE_OUTPUT)));
        Blitter sideConfigIcon = style.getImage("sideConfigIcon").copy();
        addToLeftToolbar(new ModeButton(() -> null, () -> List.of(tr("sides"), tr("sides_hint")),
                () -> { if (isSideConfigurationOpen()) backToSimulator(); else openSideConfiguration(); }) {
            @Override public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                super.extractContents(graphics, mouseX, mouseY, partialTick);
                // This asymmetric glyph includes its lower shadow. Align its visible center
                // while keeping the blit on whole framebuffer pixels at odd GUI scales.
                var window = minecraft.getWindow();
                float scaleX = (float) window.getWidth() / window.getGuiScaledWidth();
                float scaleY = (float) window.getHeight() / window.getGuiScaledHeight();
                float dx = Math.round((getX() + 0.5f) * scaleX) / scaleX - getX();
                float dy = Math.round((getY() + 1.5f) * scaleY) / scaleY - getY();
                var pose = graphics.pose();
                pose.pushMatrix();
                try {
                    pose.translate(dx, dy);
                    sideConfigIcon.dest(getX(), getY()).blit(graphics);
                } finally {
                    pose.popMatrix();
                }
            }
        });
        addToLeftToolbar(new ModeButton(() -> switch (menu.redstoneMode) {
            case 1 -> Icon.REDSTONE_HIGH; case 2 -> Icon.REDSTONE_LOW; default -> Icon.REDSTONE_IGNORE;
        }, () -> List.of(tr("redstone"), tr("redstone." + menu.redstoneMode)), () -> send(WorldSimulatorMenu.CYCLE_REDSTONE)));

        widgets.add("simulatorFluidIn", new FluidButton(() -> menu.fluidInput, "fill", WorldSimulatorMenu.FILL_INPUT));
        widgets.add("simulatorFluidOut", new FluidButton(() -> menu.fluidOutput, "drain", WorldSimulatorMenu.DRAIN_OUTPUT));
        sides = new WorldSimulatorSidesScreen(this);
        sides.widgets.forEach((name, widget) -> widgets.add(name, widget));
    }

    public void openSideConfiguration() {
        if (isSideConfigurationOpen()) return;
        sides.setOpen(true);
        setSlotsHidden(SlotSemantics.MACHINE_CRAFTING_GRID, true);
        setTextHidden("dialog_title", true);
    }

    public void backToSimulator() {
        if (!isSideConfigurationOpen()) return;
        sides.setOpen(false);
        setSlotsHidden(SlotSemantics.MACHINE_CRAFTING_GRID, false);
        setTextHidden("dialog_title", false);
    }

    public boolean isSideConfigurationOpen() { return sides.isOpen(); }

    public Rect2i faceButtonBounds(RelativeSide side) { return sides.faceBounds(side); }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        sides.update(menu);
    }

    static Component tr(String suffix) { return Component.translatable(LABEL + suffix); }

    private static class ModeButton extends IconButton {
        private final Supplier<Icon> icon;
        private final Supplier<List<Component>> tooltip;
        ModeButton(Supplier<Icon> icon, Supplier<List<Component>> tooltip, Runnable click) {
            super(button -> click.run());
            this.icon = icon;
            this.tooltip = tooltip;
        }
        @Override protected Icon getIcon() { return icon.get(); }
        @Override public List<Component> getTooltipMessage() { return tooltip.get(); }
    }

    private boolean handleTankClearClick(double mouseX, double mouseY, int button) {
        int x = leftPos, y = topPos;
        int target = -1;
        if (button == 1 && mouseY >= y + TANK_Y && mouseY < y + TANK_Y + TANK_HEIGHT) {
            if (mouseX >= x + INPUT_X && mouseX < x + INPUT_X + TANK_WIDTH)
                target = WorldSimulatorMenu.CLEAR_INPUT;
            else if (mouseX >= x + OUTPUT_X && mouseX < x + OUTPUT_X + TANK_WIDTH)
                target = WorldSimulatorMenu.CLEAR_OUTPUT;
        }
        long now = System.nanoTime();
        if (pendingClear != -1 && (target != pendingClear || now - pendingClearTime > 250_000_000L)) {
            send(WorldSimulatorMenu.CANCEL_CLEAR);
            pendingClear = -1;
        }
        if (target < 0) return false;
        send(target);
        pendingClear = target == pendingClear ? -1 : target;
        pendingClearTime = now;
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return handleTankClearClick(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void drawBG(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTicks);
        // Match AE2's recessed slot frame, leaving the player and upgrades chrome native.
        var inputSlots = menu.getSlots(SlotSemantics.MACHINE_CRAFTING_GRID);
        for (var slot : inputSlots) {
            drawItemWell(graphics, x + slot.x - 1, y + slot.y - 1);
        }
        if (!inputSlots.isEmpty()) {
            var topLeft = inputSlots.getFirst();
            int left = x + topLeft.x - 2;
            int top = y + topLeft.y - 1;
            // A single quiet outer edge groups the three rows; the top/bottom line up with both tanks.
            graphics.fill(left, top, left + 1, top + TANK_HEIGHT, RECESS_SHADOW);
            graphics.fill(left + TANK_HEIGHT + 1, top, left + TANK_HEIGHT + 2, top + TANK_HEIGHT, RECESS_LIGHT);
        }
        for (var slot : menu.getSlots(SlotSemantics.MACHINE_OUTPUT)) {
            drawItemWell(graphics, x + slot.x - 1, y + slot.y - 1);
        }
        for (var semantic : List.of(SlotSemantics.PLAYER_INVENTORY, SlotSemantics.PLAYER_HOTBAR)) {
            for (var slot : menu.getSlots(semantic)) {
                Blitter.icon(Icon.SLOT_BACKGROUND).dest(x + slot.x - 1, y + slot.y - 1).blit(graphics);
            }
        }
        drawTank(graphics, x + INPUT_X, y + TANK_Y, menu.fluidInput);
        drawTank(graphics, x + OUTPUT_X, y + TANK_Y, menu.fluidOutput);
        sides.draw(graphics, font, x, y);
    }

    private static void drawRecess(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int inside) {
        graphics.fill(x, y, x + width, y + height, RECESS_LIGHT);
        graphics.fill(x, y, x + width - 1, y + height - 1, RECESS_SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, inside);
    }

    private static void drawItemWell(GuiGraphicsExtractor graphics, int x, int y) {
        drawRecess(graphics, x, y, 18, 18, ITEM_WELL);
    }

    private void drawTank(GuiGraphicsExtractor graphics, int x, int y, GenericStack stack) {
        drawRecess(graphics, x, y, TANK_WIDTH, TANK_HEIGHT, FLUID_WELL);
        if (stack == null || stack.amount() <= 0 || menu.tankCapacity <= 0) {
            appeng.client.gui.style.Blitter.icon(Icon.TYPE_FILTER_FLUIDS).dest(x + 1, y + 19).blit(graphics);
            return;
        }
        int inner = TANK_HEIGHT - 2;
        int height = (int) Math.min(inner, (inner * stack.amount() + menu.tankCapacity - 1) / menu.tankCapacity);
        int bottom = y + TANK_HEIGHT - 1;
        graphics.enableScissor(x + 1, bottom - height, x + TANK_WIDTH - 1, bottom);
        try {
            for (int tile = bottom - 16; tile + 16 > bottom - height; tile -= 16) {
                AEKeyRendering.drawInGui(minecraft, graphics, x + 1, tile, stack.what());
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private final class FluidButton extends IconButton {
        private final Supplier<GenericStack> stack;
        private final String action;

        FluidButton(Supplier<GenericStack> stack, String action, int id) {
            super(ignored -> send(id));
            this.stack = stack;
            this.action = action;
            setDisableBackground(true);
            setWidth(TANK_WIDTH);
            setHeight(TANK_HEIGHT);
        }

        @Override
        protected Icon getIcon() {
            return null; // Tank art is drawn below this full-height interaction surface.
        }

        @Override
        public List<Component> getTooltipMessage() {
            var contents = stack.get();
            var fluid = contents == null ? Component.translatable(LABEL + "empty") : contents.what().getDisplayName();
            return List.of(Component.translatable(LABEL + action, fluid,
                    contents == null ? 0 : contents.amount(), menu.tankCapacity), tr("clear_tank_hint"));
        }
    }
}
