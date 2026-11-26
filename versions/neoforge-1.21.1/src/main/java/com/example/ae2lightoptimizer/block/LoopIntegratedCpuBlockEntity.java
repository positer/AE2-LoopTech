package com.example.ae2lightoptimizer.block;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import com.example.ae2lightoptimizer.storage.LoopStorageCellItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public final class LoopIntegratedCpuBlockEntity extends CraftingBlockEntity {
    public static final int CELL_SLOTS = 20;

    private final AppEngInternalInventory cells = new AppEngInternalInventory(null, CELL_SLOTS, 1) {
        @Override
        protected void onContentsChanged(int slot) {
            updateCapacity();
            if (getLevel() != null && !getLevel().isClientSide()) {
                saveChanges();
            }
        }
    };
    private long storageBytes;
    private long fluidBytes;

    public LoopIntegratedCpuBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOP_INTEGRATED_CPU.get(), pos, state);
    }

    public InternalInventory cells() {
        return cells;
    }

    private void updateCapacity() {
        long items = 0;
        long fluids = 0;
        for (var stack : cells) {
            long universalBytes = universalCellBytes(stack);
            if (universalBytes > 0) {
                long bytes = universalBytes;
                items = addCapacity(items, bytes);
                fluids = addCapacity(fluids, bytes);
            } else if (stack.getItem() instanceof IBasicCellItem basicCell && basicCell.isStorageCell(stack)) {
                long bytes = Math.max(0, basicCell.getBytes(stack));
                if (basicCell.getKeyType() == AEKeyType.items()) {
                    items = addCapacity(items, bytes);
                } else if (basicCell.getKeyType() == AEKeyType.fluids()) {
                    fluids = addCapacity(fluids, bytes);
                }
            }
        }
        storageBytes = items;
        fluidBytes = fluids;
    }

    private static long universalCellBytes(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof LoopStorageCellItem loopCell) {
            return loopCell.tier().singleTypeCapacityBytes();
        }
        var id = BuiltInRegistries.ITEM.getKey(item);
        if (!id.getNamespace().equals("ae2omnicells") || !id.getPath().startsWith("quantum_omni_cell_")) {
            return 0;
        }
        // Omni Cells' two supported versions expose the nominal capacity on the cell item.
        try {
            long bytes = ((Number) item.getClass().getMethod("getTotalBytes").invoke(item)).longValue();
            return bytes == -1 ? Long.MAX_VALUE : Math.max(0, bytes);
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    public static long addCapacity(long current, long additional) {
        return current + Math.min(Long.MAX_VALUE - current, additional);
    }

    @Override
    public long getStorageBytes() {
        return storageBytes;
    }

    public long fluidBytes() {
        return fluidBytes;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var stack : cells) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        cells.clear();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        cells.writeToNBT(tag, "cpuCells", registries);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        cells.readFromNBT(tag, "cpuCells", registries);
        updateCapacity();
    }
}
