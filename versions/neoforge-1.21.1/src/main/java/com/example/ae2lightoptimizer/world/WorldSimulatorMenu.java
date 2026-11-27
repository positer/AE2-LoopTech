package com.example.ae2lightoptimizer.world;

import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.interfaces.IProgressProvider;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

public final class WorldSimulatorMenu extends AEBaseMenu implements IProgressProvider {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Ae2LightOptimizer.NAMESPACE);
    public static final DeferredHolder<MenuType<?>, MenuType<WorldSimulatorMenu>> TYPE = MENUS.register(
            "loop_world_simulator", () -> MenuTypeBuilder.create(WorldSimulatorMenu::new, WorldSimulatorBlockEntity.class)
                    .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2LightOptimizer.NAMESPACE, "loop_world_simulator")));
    public static final int TOGGLE_ACTIVE_OUTPUT = 6;
    public static final int FILL_INPUT = 7;
    public static final int DRAIN_OUTPUT = 8;
    public static final int CYCLE_REDSTONE = 9;

    public static final int CLEAR_INPUT = 11;
    public static final int CLEAR_OUTPUT = 12;
    public static final int CANCEL_CLEAR = 13;
    private static final long CLEAR_INTERVAL_NS = 300_000_000L;

    private final WorldSimulatorBlockEntity machine;
    private int armedTank = -1;
    private long clearStartedAt;
    @GuiSync(0) public int progress;
    @GuiSync(1) public int exportFaces;
    @GuiSync(2) public boolean activeOutput;
    @GuiSync(3) public GenericStack fluidInput;
    @GuiSync(4) public GenericStack fluidOutput;
    @GuiSync(5) public int redstoneMode;
    @GuiSync(6) public int tankCapacity = WorldSimulatorBlockEntity.TANK_CAPACITY;
    @GuiSync(7) public int cycleTicks = WorldSimulatorBlockEntity.CYCLE_TICKS;
    private final int[] displayCounts = new int[WorldSimulatorBlockEntity.INPUT_SLOTS + 1];

    public WorldSimulatorMenu(MenuType<WorldSimulatorMenu> type, int id, Inventory inventory,
            WorldSimulatorBlockEntity machine) {
        super(type, id, inventory, machine);
        this.machine = machine;
        for (int slot = 0; slot < displayCounts.length; slot++) {
            int index = slot;
            addSlot(new BulkSlot(slot), slot < WorldSimulatorBlockEntity.INPUT_SLOTS
                    ? SlotSemantics.MACHINE_CRAFTING_GRID : SlotSemantics.MACHINE_OUTPUT);
            addDataSlot(new net.minecraft.world.inventory.DataSlot() {
                @Override
                public int get() {
                    return isServerSide() ? machine.getInternalInventory().getStackInSlot(index).getCount() : displayCounts[index];
                }

                @Override
                public void set(int value) {
                    displayCounts[index] = value;
                }
            });
        }
        for (int slot = 0; slot < WorldSimulatorBlockEntity.UPGRADE_SLOTS; slot++) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES, machine.upgrades(), slot),
                    SlotSemantics.UPGRADE);
        }
        createPlayerInventorySlots(inventory);
    }

    public Direction worldSide(RelativeSide relative) {
        return machine.getOrientation().getSide(relative);
    }

    public appeng.api.upgrades.IUpgradeInventory upgrades() {
        return machine.upgrades();
    }

    @Override
    public int getCurrentProgress() {
        return progress;
    }

    @Override
    public int getMaxProgress() {
        return cycleTicks;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            progress = machine.progress();
            exportFaces = machine.exportFaces();
            activeOutput = machine.activeOutput();
            redstoneMode = machine.redstoneMode();
            tankCapacity = machine.tankCapacity();
            cycleTicks = machine.cycleTicks();
            fluidInput = machine.tankContents(0);
            fluidOutput = machine.tankContents(1);
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!isServerSide() || !stillValid(player)) {
            return false;
        }
        if (id == CLEAR_INPUT || id == CLEAR_OUTPUT) {
            return handleTankClear(id == CLEAR_INPUT ? 0 : 1);
        }
        armedTank = -1;
        if (id == CANCEL_CLEAR) {
            return true;
        }
        if (id >= 0 && id < Direction.values().length) {
            machine.toggleOutputFace(Direction.values()[id]);
        } else if (id == TOGGLE_ACTIVE_OUTPUT) {
            machine.toggleActiveOutput();
        } else if (id == CYCLE_REDSTONE) {
            machine.cycleRedstone();
        } else if (id == FILL_INPUT || id == DRAIN_OUTPUT) {
            return useContainer(player, id == FILL_INPUT);
        } else {
            return false;
        }
        return true;
    }

    /** The server also sees both taps; a single menu action never deletes fluid. */
    private boolean handleTankClear(int tank) {
        if (!getCarried().isEmpty() || machine.tankContents(tank) == null) {
            armedTank = -1;
            return false;
        }
        long now = System.nanoTime();
        if (armedTank == tank && now - clearStartedAt <= CLEAR_INTERVAL_NS) {
            armedTank = -1;
            return machine.clearTank(tank);
        }
        armedTank = tank;
        clearStartedAt = now;
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        armedTank = -1;
        super.clicked(slotId, button, clickType, player);
    }

    private final class BulkSlot extends AppEngSlot {
        private final int index;

        BulkSlot(int slot) {
            super(machine.getInternalInventory(), slot);
            index = slot;
        }

        @Override
        public ItemStack getItem() {
            var stack = super.getItem();
            // Keep the vanilla slot packet and cursor within an ordinary item stack.
            return stack.getCount() > stack.getMaxStackSize() ? stack.copyWithCount(stack.getMaxStackSize()) : stack;
        }

        @Override
        public void set(ItemStack replacement) {
            var current = machine.getInternalInventory().getStackInSlot(index);
            int extra = current.getCount() - Math.min(current.getCount(), current.getMaxStackSize());
            if (!isServerSide() || extra <= 0) {
                super.set(replacement);
            } else if (replacement.isEmpty() || ItemStack.isSameItemSameComponents(current, replacement)) {
                super.set(current.copyWithCount(extra + replacement.getCount()));
            }
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            var current = machine.getInternalInventory().getStackInSlot(index);
            return index < WorldSimulatorBlockEntity.INPUT_SLOTS && super.mayPlace(stack)
                    && (current.getCount() <= current.getMaxStackSize() || ItemStack.isSameItemSameComponents(current, stack));
        }

        @Override
        public ItemStack safeInsert(ItemStack stack, int amount) {
            if (stack.isEmpty() || amount <= 0 || !mayPlace(stack)) {
                return stack;
            }
            int offered = Math.min(amount, stack.getCount());
            var remainder = machine.insertInput(index, stack.copyWithCount(offered), false);
            stack.shrink(offered - remainder.getCount());
            return stack;
        }

        @Override
        public java.util.List<net.minecraft.network.chat.Component> getCustomTooltip(ItemStack carried) {
            var stack = getItem();
            int count = displayCounts[index];
            if (!stack.isEmpty() && count > stack.getMaxStackSize()) {
                return java.util.List.of(stack.getHoverName(),
                        net.minecraft.network.chat.Component.translatable("gui.ae2lpt.world_simulator.item_count", count));
            }
            return super.getCustomTooltip(carried);
        }
    }

    private boolean useContainer(Player player, boolean fillInput) {
        var carried = getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        var inventory = new PlayerMainInvWrapper(player.getInventory());
        var result = fillInput
                ? FluidUtil.tryEmptyContainerAndStow(carried, machine.fluidPort(), inventory, Integer.MAX_VALUE, null, true)
                : FluidUtil.tryFillContainerAndStow(carried, machine.fluidPort(), inventory, Integer.MAX_VALUE, null, true);
        if (result.isSuccess()) {
            setCarried(result.getResult());
        }
        return result.isSuccess();
    }
}
