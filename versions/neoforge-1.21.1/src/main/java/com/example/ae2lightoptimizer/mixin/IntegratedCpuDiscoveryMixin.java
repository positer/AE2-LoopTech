package com.example.ae2lightoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.service.CraftingService;
import com.example.ae2lightoptimizer.block.LoopIntegratedCpuBlockEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.LinkedHashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CraftingService.class, remap = false)
abstract class IntegratedCpuDiscoveryMixin {
    // AE2 indexes machines by exact host class. Preserve its normal cluster and job registration.
    @WrapOperation(method = "updateCPUClusters", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/IGrid;getMachines(Ljava/lang/Class;)Ljava/util/Set;"))
    private Set<CraftingBlockEntity> ae2lpt$includeIntegratedCpus(IGrid grid, Class<CraftingBlockEntity> nativeType,
            Operation<Set<CraftingBlockEntity>> original) {
        var nativeCpus = original.call(grid, nativeType);
        var integratedCpus = grid.getMachines(LoopIntegratedCpuBlockEntity.class);
        if (integratedCpus.isEmpty()) {
            return nativeCpus;
        }
        var cpus = new LinkedHashSet<>(nativeCpus);
        cpus.addAll(integratedCpus);
        return cpus;
    }
}
