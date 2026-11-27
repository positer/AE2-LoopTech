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
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class WorldSimulatorBlockEntity extends AEBaseInvBlockEntity {
    public static final int INPUT_SLOTS = 9;
    public static final int TANK_CAPACITY = 8000;
    public static final int CYCLE_TICKS = 20;
    private static final int ALL_FACES = (1 << Direction.values().length) - 1;

    private final BulkInventory items = new BulkInventory();
    private final InternalInventory automationItems = new FilteredInternalInventory(items, new IAEItemFilter() {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return slot < INPUT_SLOTS;
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return slot == INPUT_SLOTS;
        }
    });
    private final FluidPort tanks = new FluidPort();
    public static final int UPGRADE_SLOTS = 4;
    private static final int[] CYCLE_LENGTH = {20, 10, 5, 3, 1};
    private final IUpgradeInventory upgrades =
            UpgradeInventories.forMachine(com.example.ae2lightoptimizer.block.ModBlocks.WORLD_SIMULATOR.get(),
                    UPGRADE_SLOTS, this::upgradesChanged);
    private final ResourceHandler<ItemResource> itemTransfer = automationItems.toResourceHandler();
    private int redstoneMode;
    private boolean activeOutput;
    private boolean transferring;
    private int work;
    private int exportFaces = ALL_FACES;
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
    public ResourceHandler<ItemResource> getExposedItemHandler(Direction side) {
        return side == null ? null : new SidePort<>(itemTransfer);
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

        changed();
    }

    public int redstoneMode() { return redstoneMode; }
    public boolean activeOutput() { return activeOutput; }

    public void cycleRedstone() {
        redstoneMode = (redstoneMode + 1) % 3;
        setChanged();
    }

    public void toggleActiveOutput() {
        activeOutput = !activeOutput;
        setChanged();
    }

    public void toggleOutputFace(Direction relativeUnrotated) {
        exportFaces ^= 1 << relativeUnrotated.ordinal();
        setChanged();
    }

    public int exportFaces() {
        return exportFaces;
    }

    public boolean autoExport() {
        return activeOutput();
    }

    public IUpgradeInventory upgrades() {
        return upgrades;
    }

    public void toggleAutoExport() {
        toggleActiveOutput();
    }

    public void toggleExport(Direction direction) {
        toggleOutputFace(direction);
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
        if (redstoneMode != 0 && (redstoneMode == 1) != level.hasNeighborSignal(worldPosition)) {
            if (getBlockState().getValue(WorldSimulatorBlock.ACTIVE)) {
                level.setBlock(worldPosition, getBlockState().setValue(WorldSimulatorBlock.ACTIVE, false), 3);
            }
            return;
        }
        if (activeOutput && level.getGameTime() % 5 == 0) {
            transferring = true;
            try {
                transferNeighbors(level);
            } finally {
                transferring = false;
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

    private void transferNeighbors(ServerLevel level) {
        int itemsOut = 64, fluidOut = 1000;
        var faces = RelativeSide.values();
        int start = (int) ((level.getGameTime() / 5) % faces.length);
        for (int i = 0; i < faces.length; i++) {
            var relative = faces[(start + i) % faces.length];
            int bit = 1 << relative.getUnrotatedSide().ordinal();
            if ((exportFaces & bit) == 0) continue;
            var side = getOrientation().getSide(relative);
            var target = worldPosition.relative(side);
            if (!level.hasChunkAt(target)) continue;
            if (itemsOut > 0) {
                var neighbor = level.getCapability(Capabilities.Item.BLOCK, target, side.getOpposite());
                if (neighbor != null) {
                    itemsOut -= move(itemTransfer, neighbor, itemsOut);
                }
            }
            if (fluidOut > 0) {
                var neighbor = level.getCapability(Capabilities.Fluid.BLOCK, target, side.getOpposite());
                if (neighbor != null) {
                    fluidOut -= move(tanks, neighbor, fluidOut);
                }
            }
        }
    }

    private static <T extends Resource> int move(ResourceHandler<T> from, ResourceHandler<T> to, int max) {
        if (max <= 0 || from == to) return 0;
        try (var tx = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.move(from, to, resource -> true, max, tx);
            if (moved > 0) tx.commit();
            return moved;
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
        upgrades.clear();
        setFluid(0, FluidStack.EMPTY);
        setFluid(1, FluidStack.EMPTY);
    }

    // Internal cursor/tank interaction; automation never receives this unsided port.
    public ResourceHandler<FluidResource> fluidPort() {
        return tanks;
    }

    public ResourceHandler<FluidResource> fluidPort(Direction side) {
        return side == null ? null : new SidePort<>(tanks);
    }

    // Face toggles govern active push only. Neighbor-initiated operations
    // still use the input/output-filtered handler unless this machine is moving resources.
    private final class SidePort<T extends Resource> extends DelegatingResourceHandler<T> {
        SidePort(ResourceHandler<T> delegate) {
            super(delegate);
        }

        @Override public int insert(int index, T resource, int amount, TransactionContext tx) {
            return !transferring ? super.insert(index, resource, amount, tx) : 0;
        }
        @Override public int insert(T resource, int amount, TransactionContext tx) {
            return !transferring ? super.insert(resource, amount, tx) : 0;
        }
        @Override public int extract(int index, T resource, int amount, TransactionContext tx) {
            return !transferring ? super.extract(index, resource, amount, tx) : 0;
        }
        @Override public int extract(T resource, int amount, TransactionContext tx) {
            return !transferring ? super.extract(resource, amount, tx) : 0;
        }
    }

    public boolean clearTank(int tank) {
        if (tank < 0 || tank >= tanks.size() || getFluid(tank).isEmpty()) {
            return false;
        }
        setFluid(tank, FluidStack.EMPTY);
        return true;
    }

    private FluidStack getFluid(int tank) {
        return FluidUtil.getStack(tanks, tank);
    }

    private void setFluid(int tank, FluidStack stack) {
        tanks.set(tank, FluidResource.of(stack), stack.getAmount());
        changed();
    }

    @Override
    public void loadTag(ValueInput data) {
        super.loadTag(data);
        tanks.deserialize(data.childOrEmpty("tanks"));
        int oldMode = data.getIntOr("activeMode", data.getBooleanOr("autoExport", false) ? 2 : 0);
        activeOutput = data.getBooleanOr("activeOutput", oldMode == 2 || oldMode == 3);
        redstoneMode = data.getIntOr("redstoneMode", 0);
        if (redstoneMode < 0 || redstoneMode > 2) redstoneMode = 0;
        upgrades.readFromNBT(data, "upgrades");
        exportFaces = data.getIntOr("exportFaces", ALL_FACES) & ALL_FACES;
        var counts = data.read("bulkCounts", com.mojang.serialization.Codec.INT.listOf()).orElse(List.of());
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
    public void saveAdditional(ValueOutput data) {
        items.saving = true;
        try {
            super.saveAdditional(data);
        } finally {
            items.saving = false;
        }
        data.store("bulkCounts", com.mojang.serialization.Codec.INT.listOf(),
                java.util.stream.IntStream.range(0, items.size()).mapToObj(slot -> items.getStackInSlot(slot).getCount()).toList());
        data.putChild("tanks", tanks);
        data.putBoolean("activeOutput", activeOutput);
        data.putInt("redstoneMode", redstoneMode);
        upgrades.writeToNBT(data, "upgrades");
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

    private final class FluidPort extends FluidStacksResourceHandler {
        FluidPort() {
            super(2, TANK_CAPACITY);
        }

        @Override
        protected int getCapacity(int index, FluidResource resource) {
            return tankCapacity();
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            changed();
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return index == 0 && super.isValid(index, resource);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return index == 1 ? super.extract(index, resource, amount, transaction) : 0;
        }
    }
}
