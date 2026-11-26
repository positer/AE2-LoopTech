package com.example.ae2lightoptimizer.part;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import com.example.ae2lightoptimizer.integration.LoopTickerPower;
import com.example.ae2lightoptimizer.integration.LoopTickerTarget;
import appeng.core.definitions.AEItems;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.parts.AEBasePart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The loop ticker as a cable part: it mounts on one face of a cable exactly like the ME output bus,
 * and accelerates whatever block sits behind that face. It draws no power of its own but only runs
 * while the grid is powered.
 */
public final class LoopTickerPart extends AEBasePart
        implements IGridTickable, IUpgradeableObject, com.example.ae2lightoptimizer.api.LoopTickerHost {
    private static final int BASE_CAP = 4;
    private static final int MAX_CARDS = 4;

    private final IUpgradeInventory upgrades;
    private final LoopTickerPower induction = new LoopTickerPower();
    private int rate;

    public LoopTickerPart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
        upgrades = UpgradeInventories.forMachine(partItem, MAX_CARDS + 1, this::onUpgradesChanged);
    }

    private void onUpgradesChanged() {
        var host = getHost();
        if (host != null) {
            host.markForSave();
            if (getLevel() instanceof ServerLevel server) {
                server.invalidateCapabilities(getBlockEntity().getBlockPos());
            }
        }
    }

    @Override
    public void addAdditionalDrops(java.util.List<net.minecraft.world.item.ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (var stack : upgrades) {
            if (!stack.isEmpty()) drops.add(stack.copy());
        }
    }

    @Override
    public void clearContent() {
        upgrades.clear();
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public int maxRate() {
        int installed = upgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
        return BASE_CAP << Math.min(installed, MAX_CARDS);
    }

    public int rate() {
        return rate <= 0 ? maxRate() : Math.min(rate, maxRate());
    }

    public void setRate(int value) {
        rate = Math.max(1, Math.min(value, maxRate()));
        if (getHost() != null && getHost().getBlockEntity() != null) {
            getHost().getBlockEntity().setChanged();
        }
    }

    /** Empty-hand right-click opens the rate menu, exactly like the native output bus. */
    @Override
    public boolean onUseWithoutItem(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.Vec3 pos) {
        if (!isClientSide()) {
            appeng.menu.MenuOpener.open(com.example.ae2lightoptimizer.menu.LoopTickerMenu.PART_TYPE.get(),
                    player, appeng.menu.locator.MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.Vec3 pos) {
        boolean inductionCard = LoopTickerPower.isCard(stack);
        if (player.isShiftKeyDown() && (inductionCard || stack.is(AEItems.SPEED_CARD.asItem()))) {
            if (!isClientSide()) {
                int start = inductionCard ? INDUCTION_SLOT : 0;
                int end = inductionCard ? INDUCTION_SLOT + 1 : MAX_CARDS;
                for (int slot = start; slot < end && !stack.isEmpty(); slot++) {
                    if (upgrades.getStackInSlot(slot).isEmpty() && upgrades.isItemValid(slot, stack)) {
                        upgrades.setItemDirect(slot, stack.copyWithCount(1));
                        stack.consume(1, player);
                    }
                }
            }
            return true;
        }
        return super.onUseItemOn(stack, player, hand, pos);
    }

    @Override
    public void saveRate() {
        if (getHost() != null && getHost().getBlockEntity() != null) {
            getHost().getBlockEntity().setChanged();
        }
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(4, 4, 12, 12, 12, 14);
        bch.addBox(5, 5, 14, 11, 11, 15);
        bch.addBox(6, 6, 15, 10, 10, 16);
        bch.addBox(6, 6, 11, 10, 10, 12);
    }

    @Override
    public void writeToNBT(ValueOutput out) {
        super.writeToNBT(out);
        upgrades.writeToNBT(out, "upgrades");
        out.putInt("ae2lfRate", rate);
    }

    @Override
    public void readFromNBT(ValueInput in) {
        super.readFromNBT(in);
        upgrades.readFromNBT(in, "upgrades");
        rate = in.getInt("ae2lfRate").orElse(0);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (node.isActive() && getLevel() instanceof ServerLevel server) {
            var origin = getBlockEntity().getBlockPos();
            var face = getSide();
            induction.push(server, origin, face, node.getGrid(), LoopTickerPower.installed(upgrades));
            LoopTickerTarget.accelerate(this, server, origin.relative(face), rate());
        }
        return TickRateModulation.SAME;
    }
}
