package com.example.ae2lightoptimizer.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.Icon;
import com.example.ae2lightoptimizer.menu.LoopIntegratedCpuMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class LoopIntegratedCpuScreen extends AEBaseScreen<LoopIntegratedCpuMenu> {
    LoopIntegratedCpuScreen(LoopIntegratedCpuMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        widgets.add("openPriority", new TabButton(Icon.PRIORITY,
                Component.translatable("gui.ae2lpt.loop_integrated_cpu.status"),
                button -> menu.openCpuStatus()));
    }
}
