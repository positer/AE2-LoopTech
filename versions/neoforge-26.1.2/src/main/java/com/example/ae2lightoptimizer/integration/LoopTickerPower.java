package com.example.ae2lightoptimizer.integration;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.IGrid;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.powersink.IExternalPowerSink;
import appeng.helpers.ForgeEnergyAdapter;
import com.example.ae2lightoptimizer.block.ModBlockEntities;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.part.LoopTickerPart;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Makes an upgraded ticker an input-only, sided AE energy acceptor. */
public final class LoopTickerPower implements IExternalPowerSink {
    private static Item inductionCard = Items.AIR;

    private final Supplier<IGrid> grid;
    private final LongSupplier tick;
    private final BooleanSupplier enabled;
    private final ForgeEnergyAdapter port = new ForgeEnergyAdapter(this);
    private long lastInputTick = Long.MIN_VALUE;
    private double receivedFe;

    public LoopTickerPower(Supplier<IGrid> grid, LongSupplier tick, BooleanSupplier enabled) {
        this.grid = grid;
        this.tick = tick;
        this.enabled = enabled;
    }

    public static void setup() {
        var id = Identifier.fromNamespaceAndPath("appflux", "induction_card");
        inductionCard = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        if (cardAvailable()) {
            Upgrades.add(inductionCard, ModBlocks.LOOP_TICKER.get(), 1);
            Upgrades.add(inductionCard, ModItems.LOOP_TICKER_PART.get(), 1);
        }
    }

    public static void registerBlockCapability(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.LOOP_TICKER.get(),
                (ticker, side) -> side == ticker.facing() ? ticker.inductionPort() : null);
    }

    public static void registerPartCapability(RegisterPartCapabilitiesEvent event) {
        event.register(Capabilities.Energy.BLOCK,
                (part, side) -> side == part.getSide() ? part.inductionPort() : null, LoopTickerPart.class);
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

    public ForgeEnergyAdapter port() {
        return enabled.getAsBoolean() ? port : null;
    }

    private IGrid enabledGrid() {
        return enabled.getAsBoolean() ? grid.get() : null;
    }

    private double remainingFe() {
        return lastInputTick == tick.getAsLong() ? Integer.MAX_VALUE - receivedFe : Integer.MAX_VALUE;
    }

    @Override
    public double getExternalPowerDemand(PowerUnit unit, double maximum) {
        var network = enabledGrid();
        if (network == null) return 0;
        double cap = Math.min(maximum, PowerUnit.FE.convertTo(unit, remainingFe()));
        double demand = network.getEnergyService().getEnergyDemand(unit.convertTo(PowerUnit.AE, cap));
        return Math.min(cap, PowerUnit.AE.convertTo(unit, demand));
    }

    @Override
    public double injectExternalPower(PowerUnit unit, double amount, Actionable mode) {
        var network = enabledGrid();
        if (network == null || amount <= 0) return amount;
        double capped = Math.min(amount, PowerUnit.FE.convertTo(unit, remainingFe()));
        double excess = network.getEnergyService().injectPower(unit.convertTo(PowerUnit.AE, capped), mode);
        double accepted = Math.max(0, capped - PowerUnit.AE.convertTo(unit, excess));
        if (mode == Actionable.MODULATE && accepted > 0) {
            double alreadyReceived = Integer.MAX_VALUE - remainingFe();
            lastInputTick = tick.getAsLong();
            receivedFe = Math.min(Integer.MAX_VALUE, alreadyReceived + unit.convertTo(PowerUnit.FE, accepted));
        }
        return amount - accepted;
    }

    @Override
    public double injectAEPower(double amount, Actionable mode) {
        return injectExternalPower(PowerUnit.AE, amount, mode);
    }

    @Override
    public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        return 0;
    }

    @Override
    public double getAEMaxPower() {
        var network = enabledGrid();
        return network == null ? 0 : network.getEnergyService().getMaxStoredPower();
    }

    @Override
    public double getAECurrentPower() {
        var network = enabledGrid();
        return network == null ? 0 : network.getEnergyService().getStoredPower();
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return false;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return enabled.getAsBoolean() ? AccessRestriction.WRITE : AccessRestriction.NO_ACCESS;
    }
}
