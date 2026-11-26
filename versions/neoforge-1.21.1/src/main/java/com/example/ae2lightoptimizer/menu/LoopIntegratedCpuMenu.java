package com.example.ae2lightoptimizer.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftingCPUMenu;
import appeng.menu.slot.RestrictedInputSlot;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.block.LoopIntegratedCpuBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LoopIntegratedCpuMenu extends AEBaseMenu {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Ae2LightOptimizer.NAMESPACE);
    public static final DeferredHolder<MenuType<?>, MenuType<LoopIntegratedCpuMenu>> TYPE = MENUS.register(
            "loop_integrated_cpu", () -> MenuTypeBuilder.create(LoopIntegratedCpuMenu::new,
                    LoopIntegratedCpuBlockEntity.class)
                    .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2LightOptimizer.NAMESPACE,
                            "loop_integrated_cpu")));

    private static final String OPEN_CPU = "openCraftingCpu";

    private static final appeng.menu.SlotSemantic SECOND_BANK =
            SlotSemantics.register("AE2LPT_CPU_SECOND_BANK", false);

    public LoopIntegratedCpuMenu(MenuType<LoopIntegratedCpuMenu> type, int id, Inventory inventory,
            LoopIntegratedCpuBlockEntity cpu) {
        super(type, id, inventory, cpu);
        for (int slot = 0; slot < LoopIntegratedCpuBlockEntity.CELL_SLOTS; slot++) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_CELLS, cpu.cells(), slot),
                    slot < LoopIntegratedCpuBlockEntity.CELL_SLOTS / 2 ? SlotSemantics.STORAGE_CELL : SECOND_BANK);
        }
        createPlayerInventorySlots(inventory);
        registerClientAction(OPEN_CPU, () -> {
            if (cpu.isFormed() && cpu.isActive()) {
                MenuOpener.open(CraftingCPUMenu.TYPE, getPlayer(), MenuLocators.forBlockEntity(cpu));
            }
        });
    }

    public void openCpuStatus() {
        sendClientAction(OPEN_CPU);
    }
}
