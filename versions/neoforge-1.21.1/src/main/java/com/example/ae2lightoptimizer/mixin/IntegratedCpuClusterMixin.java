package com.example.ae2lightoptimizer.mixin;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.example.ae2lightoptimizer.block.LoopIntegratedCpuBlockEntity;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCPUCluster.class, remap = false)
abstract class IntegratedCpuClusterMixin {
    @Unique
    private List<LoopIntegratedCpuBlockEntity> ae2lpt$diskUnits;

    @Inject(method = "addBlockEntity", at = @At("HEAD"))
    private void ae2lpt$trackDiskUnit(CraftingBlockEntity block, CallbackInfo ci) {
        if (block instanceof LoopIntegratedCpuBlockEntity diskUnit) {
            if (ae2lpt$diskUnits == null) {
                ae2lpt$diskUnits = new ArrayList<>();
            }
            ae2lpt$diskUnits.add(diskUnit);
        }
    }

    // Keep removable capacity out of AE2's fixed sum. The native cluster and its job stay intact.
    @Redirect(method = "addBlockEntity", at = @At(value = "INVOKE",
            target = "Lappeng/blockentity/crafting/CraftingBlockEntity;getStorageBytes()J"))
    private long ae2lpt$fixedStorage(CraftingBlockEntity block) {
        return block instanceof LoopIntegratedCpuBlockEntity ? 0 : block.getStorageBytes();
    }

    @Inject(method = "getAvailableStorage", at = @At("RETURN"), cancellable = true)
    private void ae2lpt$includeDiskStorage(CallbackInfoReturnable<Long> cir) {
        if (ae2lpt$diskUnits != null) {
            long bytes = cir.getReturnValueJ();
            for (var unit : ae2lpt$diskUnits) {
                bytes = LoopIntegratedCpuBlockEntity.addCapacity(bytes, unit.getStorageBytes());
            }
            cir.setReturnValue(bytes);
        }
    }

    @Inject(method = "getCoProcessors", at = @At("RETURN"), cancellable = true)
    private void ae2lpt$includeDiskCoprocessors(CallbackInfoReturnable<Integer> cir) {
        if (ae2lpt$diskUnits != null) {
            long fluidBytes = 0;
            for (var unit : ae2lpt$diskUnits) {
                fluidBytes = LoopIntegratedCpuBlockEntity.addCapacity(fluidBytes, unit.fluidBytes());
            }
            // The AE2 execution loop adds one operation to the co-processor count.
            cir.setReturnValue((int) Math.min(Integer.MAX_VALUE - 1L, cir.getReturnValueI() + fluidBytes / 256));
        }
    }
}
