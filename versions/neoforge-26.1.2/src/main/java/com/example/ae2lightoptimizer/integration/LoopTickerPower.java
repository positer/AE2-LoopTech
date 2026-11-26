package com.example.ae2lightoptimizer.integration;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Pushes ME grid energy into the adjacent machine. Never exposes an FE capability. */
public final class LoopTickerPower {
    private static Item inductionCard = Items.AIR;
    private final LoopTickerEnergyTransfer transfer = new LoopTickerEnergyTransfer();

    public static void setup() {
        var id = Identifier.fromNamespaceAndPath("appflux", "induction_card");
        inductionCard = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (cardAvailable()) {
            Upgrades.add(inductionCard, ModBlocks.LOOP_TICKER.get(), 1);
            Upgrades.add(inductionCard, ModItems.LOOP_TICKER_PART.get(), 1);
        }
    }

    public static boolean cardAvailable() {
        return inductionCard != Items.AIR;
    }

    public static boolean isCard(ItemStack stack) {
        return cardAvailable() && stack.is(inductionCard);
    }

    public static boolean installed(IUpgradeInventory inventory) {
        return cardAvailable() && inventory.isInstalled(inductionCard);
    }

    public void push(ServerLevel level, BlockPos origin, Direction facing, IGrid network, boolean enabled) {
        if (!enabled || network == null || facing == null) return;
        BlockPos target = origin.relative(facing);
        if (!level.isLoaded(target)) return;
        var receiver = level.getCapability(Capabilities.Energy.BLOCK, target, facing.getOpposite());
        if (receiver == null) return;
        var energy = network.getEnergyService();
        // AE2 ticks the part before the level advances game time; block callbacks can run later.
        transfer.push(level.getServer().getTickCount(), PowerUnit.FE.convertTo(PowerUnit.AE, 1),
                (amount, simulate) -> energy.extractAEPower(amount,
                        simulate ? Actionable.SIMULATE : Actionable.MODULATE, PowerMultiplier.ONE),
                amount -> energy.injectPower(amount, Actionable.MODULATE),
                (amount, simulate) -> {
                    try (var transaction = Transaction.openRoot()) {
                        int accepted = receiver.insert(amount, transaction);
                        if (!simulate) transaction.commit();
                        return accepted;
                    }
                });
    }
}
