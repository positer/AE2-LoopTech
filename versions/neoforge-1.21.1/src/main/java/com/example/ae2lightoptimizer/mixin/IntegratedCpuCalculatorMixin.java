package com.example.ae2lightoptimizer.mixin;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCalculator;
import com.example.ae2lightoptimizer.block.LoopIntegratedCpuBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CraftingCPUCalculator.class, remap = false)
abstract class IntegratedCpuCalculatorMixin {
    // Permit a geometrically valid empty chassis to form before a storage cell is installed.
    @Redirect(method = "verifyInternalStructure", at = @At(value = "INVOKE",
            target = "Lappeng/blockentity/crafting/CraftingBlockEntity;getStorageBytes()J"))
    private long ae2lpt$structuralStorage(CraftingBlockEntity block) {
        return block instanceof LoopIntegratedCpuBlockEntity ? 1 : block.getStorageBytes();
    }
}
