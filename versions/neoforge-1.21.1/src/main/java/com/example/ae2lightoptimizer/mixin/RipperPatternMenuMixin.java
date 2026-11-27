package com.example.ae2lightoptimizer.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.implementations.PatternProviderMenu;
import com.example.ae2lightoptimizer.menu.CraftingRipperMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Rebind only the Ripper's native pattern slots to its bounded viewport. */
@Mixin(value = PatternProviderMenu.class, remap = false)
public abstract class RipperPatternMenuMixin {
    @Shadow @Final protected PatternProviderLogic logic;

    // Decorate the menu-local inventory, after any add-on redirection of getPatternInv().
    // Keeping standard and extended providers' selected inventories intact is essential.
    @ModifyVariable(
            method = "<init>(Lnet/minecraft/world/inventory/MenuType;ILnet/minecraft/world/entity/player/Inventory;Lappeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At("STORE"), ordinal = 0, require = 1, allow = 1)
    private InternalInventory ae2lf$patternViewport(InternalInventory original) {
        return (Object) this instanceof CraftingRipperMenu menu ? menu.createPatternViewport(logic) : original;
    }
}
