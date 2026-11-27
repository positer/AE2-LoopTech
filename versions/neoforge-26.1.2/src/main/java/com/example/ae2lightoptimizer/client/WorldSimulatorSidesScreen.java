package com.example.ae2lightoptimizer.client;

import appeng.api.orientation.RelativeSide;
import appeng.client.gui.widgets.AE2Button;
import com.example.ae2lightoptimizer.world.WorldSimulatorMenu;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** The AE2-style face editor stays in the machine screen and shares its open menu. */
final class WorldSimulatorSidesScreen {
    static final int X = 27, Y = 5, WIDTH = 76, HEIGHT = 98;
    final Map<String, AE2Button> widgets = new LinkedHashMap<>();
    private final Map<RelativeSide, FaceButton> faces = new EnumMap<>(RelativeSide.class);
    private boolean open;

    WorldSimulatorSidesScreen(WorldSimulatorScreen screen) {
        widgets.put("sideClose", new AE2Button(Component.literal("×"), ignored -> screen.backToSimulator()));
        for (var side : RelativeSide.values()) {
            var button = new FaceButton(side == RelativeSide.FRONT, ignored -> screen.send(side.getUnrotatedSide().ordinal()));
            faces.put(side, button);
            widgets.put("sideFace_" + side.name().toLowerCase(Locale.ROOT), button);
        }
        setOpen(false);
    }

    boolean isOpen() { return open; }

    void setOpen(boolean value) {
        open = value;
        for (var button : widgets.values()) {
            button.visible = value;
            button.active = value;
        }
    }

    Rect2i faceBounds(RelativeSide side) {
        var button = faces.get(side);
        return new Rect2i(button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }

    void update(WorldSimulatorMenu menu) {
        if (!open) return;
        for (var side : RelativeSide.values()) {
            int bit = 1 << side.getUnrotatedSide().ordinal();
            var button = faces.get(side);
            button.output = (menu.exportFaces & bit) != 0;
            var face = WorldSimulatorScreen.tr("direction." + side.name().toLowerCase(Locale.ROOT));
            var world = WorldSimulatorScreen.tr("compass." + menu.worldSide(side).getName());
            var role = WorldSimulatorScreen.tr(button.output ? "output_enabled" : "output_disabled");
            button.setMessage(Component.translatable("gui.ae2lpt.world_simulator.face_label", face, role));
            button.setTooltip(Tooltip.create(Component.translatable("gui.ae2lpt.world_simulator.oriented_face", face, world, role)));
        }
    }

    void draw(GuiGraphicsExtractor graphics, Font font, int left, int top) {
        if (!open) return;
        int x = left + X, y = top + Y;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xff424355);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 2, 0xfff0f0f4);
        graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + HEIGHT - 3, 0xffc6c7d1);
        graphics.text(font, WorldSimulatorScreen.tr("sides_compact"), x + 4, y + 7, 0xff413f54, false);
    }

    private static final class FaceButton extends AE2Button {
        private final boolean front;
        private boolean output;
        FaceButton(boolean front, OnPress action) {
            super(Component.empty(), action);
            this.front = front;
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHovered();
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, SPRITES.get(true, output || hover), getX(), getY(), getWidth(), getHeight(), 0xffffffff);
            int x = getX(), y = getY();
            if (hover || isFocused()) {
                int ink = 0xfff1f7fe;
                graphics.fill(x + 2, y + 2, x + getWidth() - 2, y + 3, ink);
                graphics.fill(x + 2, y + getHeight() - 3, x + getWidth() - 2, y + getHeight() - 2, ink);
            }
            if (front) {
                graphics.item(new net.minecraft.world.item.ItemStack(
                    com.example.ae2lightoptimizer.block.ModBlocks.WORLD_SIMULATOR.get()), x + 2, y + 2);
            }
        }
    }
}
