package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.recipes.game.StorageCellDisassemblyRecipe;
import appeng.recipes.handlers.InscriberRecipe;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.storage.LoopStorageCellHandler;
import com.example.ae2lightoptimizer.storage.LoopStorageCellInventory;
import com.example.ae2lightoptimizer.storage.LoopStorageTier;
import com.example.ae2lightoptimizer.storage.PortableLoopStorageCellItem;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class PortableStorageAudit {
    static void run(ServerLevel level, BiConsumer<String, Boolean> check) {
        if (net.neoforged.fml.ModList.get().isLoaded("appflux")) {
            testAppliedFlux(check);
        }
        var manager = level.getServer().getRecipeManager();
        var powderId = ResourceLocation.parse("ae2lpt:loop_crystal_powder");
        var powder = manager.byKey(powderId).orElseThrow().value();
        check.accept("inscriber_powder_native_recipe", powder instanceof InscriberRecipe recipe
                && recipe.getMiddleInput().test(ModItems.LOOP_CRYSTAL.get().getDefaultInstance()));
        var source = IActionSource.empty();
        var key = AEItemKey.of(Items.COBBLESTONE);
        for (var tier : LoopStorageTier.values()) {
            for (var portable : new boolean[]{false, true}) {
                var id = (portable ? "portable_" : "") + tier.id() + "_loop_storage_cell";
                var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2lpt:" + id));
                var stack = item.getDefaultInstance();
                var target = LoopStorageCellHandler.INSTANCE.getCellInventory(stack, null);
                boolean tierOk = target instanceof LoopStorageCellInventory inv && inv.tier() == tier;
                check.accept(id + "_bound_to_tier", tierOk);
                if (!tierOk) continue;
                long expected = tier.infinite() ? (1L << 40) : 1;
                long simulated = target.insert(key, expected, Actionable.SIMULATE, source);
                boolean emptyAfterSimulation = target.extract(key, expected, Actionable.SIMULATE, source) == 0;
                long inserted = target.insert(key, expected, Actionable.MODULATE, source);
                target.persist();
                var decoded = LoopStorageCellHandler.INSTANCE.getCellInventory(stack.copy(), null);
                check.accept(id + "_store_persist_extract", simulated == expected && emptyAfterSimulation
                        && inserted == expected && decoded != null
                        && decoded.extract(key, expected, Actionable.MODULATE, source) == expected);
                if (item instanceof PortableLoopStorageCellItem cell) {
                    var fresh = item.getDefaultInstance();
                    var max = cell.getAEMaxPower(fresh);
                    double initial = cell.getAECurrentPower(fresh);
                    cell.injectAEPower(fresh, max / 2, Actionable.MODULATE);
                    double after = cell.getAECurrentPower(fresh);
                    cell.extractAEPower(fresh, max / 4, Actionable.MODULATE);
                    check.accept(id + "_battery_conservation", initial == 0
                            && Math.abs(after - max / 2) < 0.01
                            && Math.abs(cell.getAECurrentPower(fresh) - max / 4) < 0.01);
                    var upgrades = cell.getUpgrades(fresh);
                    upgrades.setItemDirect(0, AEItems.ENERGY_CARD.stack());
                    check.accept(id + "_energy_card", cell.getAEMaxPower(fresh) > max
                            && cell.getChargeRate(fresh) > 80);
                    var parts = StorageCellDisassemblyRecipe.getDisassemblyResult(level, item);
                    check.accept(id + "_disassembly_loaded", parts.size() >= 3
                            && parts.stream().anyMatch(part -> part.is(appeng.core.definitions.AEBlocks.ENERGY_CELL.asItem())));
                }
            }
        }
    }

    private static void testAppliedFlux(BiConsumer<String, Boolean> check) {
        try {
            String prefix = "com.glodblock.github.appflux.common.me.key.";
            Class<?> energy = Class.forName(prefix + "type.EnergyType");
            var key = (appeng.api.stacks.AEKey) Class.forName(prefix + "FluxKey")
                    .getMethod("of", energy).invoke(null, energy.getField("FE").get(null));
            var item = ModItems.PORTABLE_LOOP_STORAGE_CELLS.getFirst().get();
            var stack = item.getDefaultInstance();
            var target = LoopStorageCellHandler.INSTANCE.getCellInventory(stack, null);
            check.accept("applied_flux_native_key_stored", target.insert(key, 1000,
                    Actionable.MODULATE, IActionSource.empty()) == 1000);
            target.persist();
            check.accept("applied_flux_simulated_debit_preserves_energy",
                    com.example.ae2lightoptimizer.storage.PortableLoopEnergy.extractStoredFe(stack, 20, true) == 20
                    && com.example.ae2lightoptimizer.storage.PortableLoopEnergy.storedFe(stack) == 1000);
            com.example.ae2lightoptimizer.storage.PortableLoopEnergy.recharge(stack, item);
            check.accept("applied_flux_no_loop_card_no_recharge",
                    item.getAECurrentPower(stack) == 0
                    && com.example.ae2lightoptimizer.storage.PortableLoopEnergy.storedFe(stack) == 1000);
            item.getUpgrades(stack).setItemDirect(0, ModItems.LOOP_CARD.get().getDefaultInstance());
            com.example.ae2lightoptimizer.storage.PortableLoopEnergy.recharge(stack, item);
            long used = 1000 - com.example.ae2lightoptimizer.storage.PortableLoopEnergy.storedFe(stack);
            double equivalent = appeng.api.config.PowerUnit.FE.convertTo(appeng.api.config.PowerUnit.AE, used);
            check.accept("applied_flux_loop_card_recharge_conservation", used > 0
                    && Math.abs(item.getAECurrentPower(stack) - equivalent) < 0.01);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Installed Applied Flux FE key API unavailable", e);
        }
    }
}
