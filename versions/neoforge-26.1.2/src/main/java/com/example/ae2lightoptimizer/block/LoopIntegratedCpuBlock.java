package com.example.ae2lightoptimizer.block;

import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.block.crafting.ICraftingUnitType;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.menu.LoopIntegratedCpuMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class LoopIntegratedCpuBlock extends AbstractCraftingUnitBlock<LoopIntegratedCpuBlockEntity> {
    private enum UnitType implements ICraftingUnitType {
        INTEGRATED;

        @Override
        public long getStorageBytes() {
            return 0;
        }

        @Override
        public int getAcceleratorThreads() {
            return 0;
        }

        @Override
        public Item getItemFromType() {
            return ModItems.LOOP_INTEGRATED_CPU.get();
        }
    }

    public LoopIntegratedCpuBlock(Properties properties) {
        super(properties, UnitType.INTEGRATED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LoopIntegratedCpuBlockEntity cpu)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            var locator = MenuLocators.forBlockEntity(cpu);
            if (player.isShiftKeyDown() && cpu.isFormed() && cpu.isActive()) {
                MenuOpener.open(CraftingCPUMenu.TYPE, player, locator);
            } else {
                MenuOpener.open(LoopIntegratedCpuMenu.TYPE.get(), player, locator);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean upgrade(ItemStack stack, BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, Player player,
            BlockHitResult hit) {
        return false;
    }

    @Override
    public InteractionResult removeUpgrade(Level level, Player player, BlockPos pos, BlockState state) {
        return InteractionResult.FAIL;
    }
}
