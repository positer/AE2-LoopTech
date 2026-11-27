package com.example.ae2lightoptimizer.world;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.orientation.RelativeSide;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.AEBaseInvBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.example.ae2lightoptimizer.block.ModBlockEntities;
import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public final class WorldSimulatorBlockEntity extends AEBaseInvBlockEntity {
    public static final int INPUT_SLOTS = 9;
    public static final int TANK_CAPACITY = 8000;
    public static final int CYCLE_TICKS = 20;
    private static final int ALL_FACES = (1 << Direction.values().length) - 1;

    private final BulkInventory items = new BulkInventory();
    private final EnumMap<Direction, IItemHandler> sidedItems = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, IFluidHandler> sidedFluids = new EnumMap<>(Direction.class);
    private final FluidTank[] tanks = {newTank(), newTank()};
    private final IFluidHandler fluidPort = new FluidPort(null); // manual menu only
    public static final int UPGRADE_SLOTS = 4;
    private static final int[] CYCLE_LENGTH = {20, 10, 5, 3, 1};
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(com.example.ae2lightoptimizer.block.ModBlocks.WORLD_SIMULATOR.get(),
                    UPGRADE_SLOTS, this::upgradesChanged);
    private int work;
    private int exportFaces = ALL_FACES;
    private boolean activeOutput;
    private int redstoneMode;
    private boolean moving;
    private final int[] pushCursor = new int[6];
    private int faceCursor;
    private static final class Budget {
        int items = 64;
        int fluid = 1000;
    }
    // Normally empty. Keep a remainder if an external handler changes its offer
    // between simulate and execute rather than dropping/deleting the difference.
    private ItemStack pendingPull = ItemStack.EMPTY;
    private final FluidTank pendingFluid = new FluidTank(Integer.MAX_VALUE);
    private boolean dirty = true;
    private List<WorldRecipeCatalog.Entry> catalog;
    private WorldRecipeCatalog.Plan plan;

    public WorldSimulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORLD_SIMULATOR.get(), pos, state);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return items;
    }

    @Override
    public IItemHandler getExposedItemHandler(Direction side) {
        if (side == null) return null;
        return sidedItems.computeIfAbsent(side, face -> new FilteredInternalInventory(items, new IAEItemFilter() {
            @Override public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return slot < INPUT_SLOTS && !moving;
            }
            @Override public boolean allowExtract(InternalInventory inv, int slot, int amount) {
                return slot == INPUT_SLOTS && !moving;
            }
        }).toItemHandler());
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        changed();
    }

    private void changed() {
        dirty = true;
        setChanged();
    }

    public int progress() {
        return Math.min(work, cycleTicks());
    }

    public int cycleTicks() {
        return CYCLE_LENGTH[upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)];
    }

    public int capacityMultiplier() {
        return 1 << upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD);
    }

    public int tankCapacity() {
        return TANK_CAPACITY * capacityMultiplier();
    }

    public ItemStack insertInput(int slot, ItemStack stack, boolean simulate) {
        return items.insertItem(slot, stack, simulate);
    }

    private void upgradesChanged() {
        for (var tank : tanks) { tank.setCapacity(tankCapacity()); }
        changed();
    }

    public int exportFaces() {
        return exportFaces;
    }

    public boolean autoExport() {
        return activeOutput;
    }

    public IUpgradeInventory upgrades() {
        return upgrades;
    }

    public void toggleAutoExport() {
        toggleActiveOutput();
    }

    public void toggleExport(Direction direction) { toggleOutputFace(direction); }

    public int redstoneMode() { return redstoneMode; }
    private static int validMode(int mode, int size) { return mode >= 0 && mode < size ? mode : 0; }
    public boolean activeOutput() { return activeOutput; }
    public void cycleRedstone() { redstoneMode = (redstoneMode + 1) % 3; setChanged(); }
    public void toggleActiveOutput() { activeOutput = !activeOutput; setChanged(); }
    public void toggleOutputFace(Direction relative) {
        exportFaces ^= 1 << relative.ordinal();
        setChanged();
    }

    public boolean clearTank(int tank) {
        if (tank < 0 || tank >= tanks.length || getFluid(tank).isEmpty()) return false;
        setFluid(tank, FluidStack.EMPTY);
        return true;
    }

    private boolean enabled(ServerLevel level) {
        return redstoneMode == 0 || (redstoneMode == 1) == level.hasNeighborSignal(worldPosition);
    }

    public GenericStack tankContents(int tank) {
        var stack = getFluid(tank);
        return stack.isEmpty() ? null : new GenericStack(AEFluidKey.of(stack), stack.getAmount());
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WorldSimulatorBlockEntity machine) {
        if (level instanceof ServerLevel server) {
            machine.tick(server);
        }
    }

    private void tick(ServerLevel level) {
        if (!enabled(level)) {
            if (getBlockState().getValue(WorldSimulatorBlock.ACTIVE)) {
                level.setBlock(worldPosition, getBlockState().setValue(WorldSimulatorBlock.ACTIVE, false), 3);
            }
            return;
        }
        if (level.getGameTime() % 5 == 0) {
            restoreLegacyPull();
            if (activeOutput) {
                moving = true;
                try {
                    export(level);
                    faceCursor = (faceCursor + 1) % 6;
                } finally { moving = false; }
            }
        }
        var entries = WorldRecipeCatalog.entries(level.getServer());
        if (catalog != entries || dirty) {
            var previous = plan == null ? null : plan.recipe();
            boolean reloaded = catalog != entries;
            catalog = entries;
            plan = findPlan(entries);
            dirty = false;
            if (reloaded || plan == null || plan.recipe() != previous) {
                work = 0;
            }
        }
        if (plan != null && ++work >= cycleTicks()) {
            finish(plan);
            dirty = true;
            work = 0;

        }
        if (getBlockState().getValue(WorldSimulatorBlock.ACTIVE) != (plan != null)) {
            level.setBlock(worldPosition, getBlockState().setValue(WorldSimulatorBlock.ACTIVE, plan != null), 3);
        }
    }

    private WorldRecipeCatalog.Plan findPlan(List<WorldRecipeCatalog.Entry> entries) {
        var inputs = new ItemStack[INPUT_SLOTS];
        boolean any = false;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            inputs[slot] = items.getStackInSlot(slot);
            any |= !inputs[slot].isEmpty();
        }
        if (!any) {
            return null;
        }
        var output = items.getStackInSlot(INPUT_SLOTS);
        var fluidIn = getFluid(0);
        var fluidOut = getFluid(1);
        for (var entry : entries) {
            var candidate = entry.plan(inputs, output, fluidIn, fluidOut, capacityMultiplier());
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private void finish(WorldRecipeCatalog.Plan batch) {
        var recipe = batch.recipe();
        int count = batch.items().batches();
        var product = recipe.result().copy();
        if (!product.isEmpty()) {
            var prepare = recipe.prepareResult();
            if (prepare != null) {
                try {
                    prepare.accept(product);
                } catch (RuntimeException ex) {
                    LogUtils.getLogger().error("Cannot produce world simulator result for {}", recipe.id(), ex);
                    return;
                }
            }
            product.setCount(product.getCount() * count + items.getStackInSlot(INPUT_SLOTS).getCount());
        }
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            int consumed = batch.items().consumed()[slot];
            if (consumed > 0) {
                var stack = items.getStackInSlot(slot);
                items.setItemDirect(slot, stack.copyWithCount(stack.getCount() - consumed));
            }
        }
        if (!product.isEmpty()) {
            items.setItemDirect(INPUT_SLOTS, product);
        }
        if (!batch.fluid().isEmpty()) {
            int amount = 1000 * count;
            var input = getFluid(0);
            setFluid(0, input.copyWithAmount(input.getAmount() - amount));
            setFluid(1, batch.fluid().copyWithAmount(getFluid(1).getAmount() + amount));
        }
        setChanged();
    }

    // Restore only material already saved inside this machine by the old implementation.
    private void restoreLegacyPull() {
        if (!pendingPull.isEmpty()) {
            int before = pendingPull.getCount();
            for (int to = 0; to < INPUT_SLOTS && !pendingPull.isEmpty(); to++)
                pendingPull = items.insertItem(to, pendingPull, false);
            if (before != pendingPull.getCount()) setChanged();
        }
        if (!pendingFluid.isEmpty()) {
            int accepted = tanks[0].fill(pendingFluid.getFluid(), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) {
                pendingFluid.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                setChanged();
            }
        }
    }

    private void export(ServerLevel level) {
        if (items.getStackInSlot(INPUT_SLOTS).isEmpty() && getFluid(1).isEmpty()) {
            return;
        }
        var budget = new Budget();
        var faces = RelativeSide.values();
        for (int step = 0; step < faces.length && (budget.items > 0 || budget.fluid > 0); step++) {
            var relative = faces[(faceCursor + step) % faces.length];
            if (!mayExport(relative)) {
                continue;
            }
            var side = getOrientation().getSide(relative);
            var target = worldPosition.relative(side);
            if (!level.hasChunkAt(target)) {
                continue;
            }
            exportTo(level, target, side.getOpposite(), budget);
        }
    }

    private boolean mayExport(RelativeSide side) {
        return (exportFaces & (1 << side.getUnrotatedSide().ordinal())) != 0;
    }

    private void exportTo(ServerLevel level, BlockPos target, Direction targetSide, Budget budget) {
        var item = items.getStackInSlot(INPUT_SLOTS);
        if (budget.items > 0 && !item.isEmpty()) {
            var storage = level.getCapability(Capabilities.ItemHandler.BLOCK, target, targetSide);
            if (storage != null && storage.getSlots() > 0) {
                int offered = Math.min(Math.min(budget.items, item.getCount()), item.getMaxStackSize());
                int remaining = offered;
                int slots = storage.getSlots(), face = targetSide.ordinal();
                int start = Math.floorMod(pushCursor[face], slots);
                for (int j = 0; j < Math.min(slots, 32) && remaining > 0; j++) {
                    int slot = (int) (((long) start + j) % slots);
                    pushCursor[face] = (slot + 1) % slots;
                    var leftover = storage.insertItem(slot, item.copyWithCount(remaining), false);
                    remaining = Math.clamp(leftover.getCount(), 0, remaining);
                }
                int accepted = offered - remaining;
                if (accepted > 0) {
                    budget.items -= accepted;
                    items.setItemDirect(INPUT_SLOTS, item.copyWithCount(item.getCount() - accepted));
                }
            }
        }
        var fluid = getFluid(1);
        if (budget.fluid > 0 && !fluid.isEmpty()) {
            var storage = level.getCapability(Capabilities.FluidHandler.BLOCK, target, targetSide);
            if (storage != null) {
                int offered = Math.min(budget.fluid, fluid.getAmount());
                int accepted = Math.clamp(storage.fill(fluid.copyWithAmount(offered),
                        IFluidHandler.FluidAction.EXECUTE), 0, offered);
                if (accepted > 0) {
                    budget.fluid -= accepted;
                    setFluid(1, fluid.copyWithAmount(fluid.getAmount() - accepted));
                }
            }
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        for (var stack : items) {
            for (int remaining = stack.getCount(); remaining > 0; remaining -= stack.getMaxStackSize()) {
                drops.add(stack.copyWithCount(Math.min(stack.getMaxStackSize(), remaining)));
            }
        }
        upgrades.forEach(drops::add);
        if (!pendingPull.isEmpty()) drops.add(pendingPull.copy());
        if (!pendingFluid.isEmpty()) {
            AEFluidKey.of(pendingFluid.getFluid()).addDrops(pendingFluid.getFluidAmount(), drops, level, pos);
        }
        for (int tank = 0; tank < 2; tank++) {
            var contents = tankContents(tank);
            if (contents != null) {
                contents.what().addDrops(contents.amount(), drops, level, pos);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        pendingPull = ItemStack.EMPTY;
        pendingFluid.setFluid(FluidStack.EMPTY);
        upgrades.clear();
        setFluid(0, FluidStack.EMPTY);
        setFluid(1, FluidStack.EMPTY);
    }

    public IFluidHandler fluidPort() {
        return fluidPort;
    }

    public IFluidHandler fluidPort(Direction side) {
        return side == null ? null : sidedFluids.computeIfAbsent(side, FluidPort::new);
    }

    private FluidStack getFluid(int tank) {
        return tanks[tank].getFluid();
    }

    private void setFluid(int tank, FluidStack stack) {
        tanks[tank].setFluid(stack);
        changed();
    }

    private FluidTank newTank() {
        return new FluidTank(TANK_CAPACITY) {
            @Override
            protected void onContentsChanged() {
                changed();
            }
        };
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        pendingPull = ItemStack.parseOptional(registries, data.getCompound("pendingPull"));
        pendingFluid.readFromNBT(registries, data.getCompound("pendingFluid"));
        tanks[0].readFromNBT(registries, data.getCompound("inputTank"));
        tanks[1].readFromNBT(registries, data.getCompound("outputTank"));
        if (data.contains("activeOutput")) {
            activeOutput = data.getBoolean("activeOutput");
        } else if (data.contains("activeMode")) {
            int mode = data.getInt("activeMode");
            activeOutput = mode == 2 || mode == 3;
        } else {
            activeOutput = data.getBoolean("autoExport");
        }
        redstoneMode = data.contains("redstoneMode") ? validMode(data.getInt("redstoneMode"), 3) : 0;
        upgrades.readFromNBT(data, "upgrades", registries);
        exportFaces = data.contains("exportFaces") ? data.getInt("exportFaces") & ALL_FACES : ALL_FACES;
        var counts = java.util.Arrays.stream(data.getIntArray("bulkCounts")).boxed().toList();
        for (int slot = 0; slot < Math.min(items.size(), counts.size()); slot++) {
            var stack = items.getStackInSlot(slot);
            int count = counts.get(slot);
            if (!stack.isEmpty() && count >= stack.getCount() && count <= stack.getMaxStackSize() * (1 << UPGRADE_SLOTS)) {
                items.setItemDirect(slot, stack.copyWithCount(count));
            }
        }
        upgradesChanged();
        dirty = true;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        items.saving = true;
        try {
            super.saveAdditional(data, registries);
        } finally {
            items.saving = false;
        }
        data.putIntArray("bulkCounts", java.util.stream.IntStream.range(0, items.size())
                .map(slot -> items.getStackInSlot(slot).getCount()).toArray());
        data.put("inputTank", tanks[0].writeToNBT(registries, new CompoundTag()));
        data.put("outputTank", tanks[1].writeToNBT(registries, new CompoundTag()));
        if (!pendingPull.isEmpty()) data.put("pendingPull", pendingPull.save(registries));
        data.put("pendingFluid", pendingFluid.writeToNBT(registries, new CompoundTag()));
        data.putBoolean("activeOutput", activeOutput);
        data.putInt("redstoneMode", redstoneMode);
        data.putBoolean("autoExport", autoExport());
        upgrades.writeToNBT(data, "upgrades", registries);
        data.putInt("exportFaces", exportFaces);
    }

    private final class BulkInventory extends AppEngInternalInventory {
        private boolean saving;

        BulkInventory() {
            super(WorldSimulatorBlockEntity.this, INPUT_SLOTS + 1);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64 * capacityMultiplier();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            var stack = super.getStackInSlot(slot);
            return saving && stack.getCount() > stack.getMaxStackSize()
                    ? stack.copyWithCount(stack.getMaxStackSize()) : stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack incoming, boolean simulate) {
            if (incoming.isEmpty() || !isItemValid(slot, incoming)) {
                return incoming;
            }
            var current = getStackInSlot(slot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, incoming)) {
                return incoming;
            }
            int space = incoming.getMaxStackSize() * capacityMultiplier() - current.getCount();
            int amount = Math.min(incoming.getCount(), space);
            if (amount <= 0) {
                return incoming;
            }
            if (!simulate) {
                setItemDirect(slot, incoming.copyWithCount(current.getCount() + amount));
            }
            return incoming.copyWithCount(incoming.getCount() - amount);
        }
    }

    private final class FluidPort implements IFluidHandler {
        private final Direction side;
        FluidPort(Direction side) { this.side = side; }
        private boolean allowed() { return side == null || !moving; }
        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return getFluid(tank).copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return tankCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && allowed() && tanks[0].isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return allowed() && tanks[0].getFluidAmount() < tankCapacity() ? tanks[0].fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return allowed() ? tanks[1].drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            return allowed() ? tanks[1].drain(amount, action) : FluidStack.EMPTY;
        }
    }
}
