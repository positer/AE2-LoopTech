package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.world.WorldSimulatorBlockEntity;
import com.example.ae2lightoptimizer.world.WorldSimulatorMenu;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

final class WorldSimulatorFixture {
    static final boolean ENABLED = "world_simulator".equals(System.getProperty("ae2lo.probe.chain"));
    static final String WORLD_FRAME = "ae2lo-ripper-world-simulator-block.png";
    static final String MENU_FRAME = "ae2lo-ripper-world-simulator-menu.png";
    static final String SIDES_FRAME = "ae2lo-ripper-world-simulator-sides.png";
    static final String EMPTY_FRAME = "ae2lo-ripper-world-simulator-progress-empty.png";
    static final String HOVER_FRAME = "ae2lo-ripper-world-simulator-sides-hover.png";
    static final String HELP_FRAME = "ae2lo-ripper-world-simulator-help.png";
    static final String ACTIVE_ON_FRAME = "ae2lo-ripper-world-simulator-active-output-on.png";
    static final BlockPos ORIGIN = new BlockPos(-3, 101, 0);
    static final BlockPos EXTERIOR_POS = ORIGIN.offset(0, 3, -7);
    private static final LinkedHashMap<String, Object> REPORT = new LinkedHashMap<>();
    private static final WorldSimulatorBlockEntity[] MACHINES = new WorldSimulatorBlockEntity[12];
    private static WorldSimulatorBlockEntity recipient;
    private static long simultaneousTick = -1;
    private static ServerLevel level;
    private static ServerPlayer player;
    private static Path output;
    private static int ticks;

    private WorldSimulatorFixture() {}

    static void start(MinecraftServer server, ServerPlayer probePlayer, Path evidence) {
        level = server.overworld();
        player = probePlayer;
        output = evidence;
        REPORT.put("minecraft", "1.21.1");
        REPORT.put("chain", "world_simulator");
        REPORT.put("world", server.getWorldData().getLevelName());
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 0; z++) {
                level.setChunkForced(x, z, true);
            }
        }
        player.setGameMode(GameType.CREATIVE);
        player.connection.teleport(-2.5, 101.0, -3.0, 0.0F, 5.0F);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    static void tick() {
        try {
            ++ticks;
            if (ticks >= 62 && ticks <= 68) observeOneTransferCycle();
            switch (ticks) {
                case 40 -> setup();
                case 49 -> check("progress_tick_9", MACHINES[0].progress() == 9 && output(0).isEmpty());
                case 59 -> {
                    check("no_early_output", output(0).isEmpty() && output(1).isEmpty() && MACHINES[0].progress() == 19);
                    check("insufficient_infinite_material", output(2).isEmpty() && MACHINES[2].progress() == 0);
                }
                case 60 -> {
                    firstBatch();
                    check("standalone_no_power_or_grid_neighbor", hasNoNetworkOrPower(MACHINES[8], true));
                    check("standalone_recipe_2_fluix_plus_1000_water",
                            output(8).is(AEItems.FLUIX_CRYSTAL.asItem()) && output(8).getCount() == 2
                            && MACHINES[8].tankContents(1).what().equals(AEFluidKey.of(Fluids.WATER))
                            && fluidAmount(8, 1) == 1000 && fluidAmount(8, 0) == 0
                            && count(8, 0) == 0 && count(8, 1) == 0 && count(8, 2) == 0);
                    check("standalone_sink_initially_empty", receivedItems() == 0 && receivedFluid() == 0);
                    ProbeState.screenshotRequest = WORLD_FRAME;
                }
                case 61 -> {
                    MACHINES[8].toggleActiveOutput();
                    check("standalone_global_push_enabled", MACHINES[8].activeOutput());
                    insert(2, 0, "ae2lpt:256m_loop_storage_core", 1);
                    MACHINES[0].getInternalInventory().setItemDirect(9, ItemStack.EMPTY);
                    MACHINES[0].upgrades().setItemDirect(0, AEItems.SPEED_CARD.stack());
                    insert(0, 2, "minecraft:tnt", 1);
                    MACHINES[7].upgrades().clear();
                    fill(1, Direction.DOWN, Fluids.WATER, 1000);
                }
                case 68 -> {
                    // Active transfer runs on a five-tick cadence before crafting.
                    checkDirectionalExport();
                    check("downsize_retains_bulk", output(7).getCount() == 80 && fluidAmount(7, 1) == 40000
                            && count(7, 3) == 128);
                    check("one_cycle_adjacent_item_plus_fluid_output", simultaneousTick >= 0
                            && receivedItems() == 2 && receivedFluid() == 1000
                            && output(8).isEmpty() && fluidAmount(8, 1) == 0);
                    REPORT.put("simultaneousPushObservation", java.util.Map.of("gameTick", simultaneousTick,
                            "neighborItems", receivedItems(), "neighborFluidMb", receivedFluid(),
                            "sourceItemRemainder", output(8).getCount(), "sourceFluidRemainderMb", fluidAmount(8, 1)));
                    placePoweredIndependentCable();
                    player.connection.teleport(0.5, 101.0, -2.4, 0.0F, 8.0F);
                }
                case 70 -> check("speed_card_nine_ticks", output(0).isEmpty() && MACHINES[0].progress() == 9);
                case 71 -> check("speed_card_ten_ticks", output(0).getCount() == 2);
                case 72 -> {
                    var pos = MACHINES[1].getBlockPos();
                    level.getBlockState(pos).useWithoutItem(level, player,
                            new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
                }
                case 82 -> {
                    check("infinite_disk", output(2).is(ModItems.INFINITE_LOOP_STORAGE_CELL.get())
                            && output(2).getCount() == 1 && count(2, 0) == 0 && count(2, 1) == 0 && count(2, 2) == 0);
                    check("full_tank_preserves_inputs", count(1, 0) == 1 && fluidAmount(1, 0) == 1000
                            && fluidAmount(1, 1) == 8000 && output(1).getCount() == 16);
                    var menu = (WorldSimulatorMenu) player.containerMenu;
                    menu.setCarried(new ItemStack(Items.BUCKET));
                    check("menu_drain_container", menu.clickMenuButton(player, WorldSimulatorMenu.DRAIN_OUTPUT)
                            && menu.getCarried().is(Items.WATER_BUCKET) && fluidAmount(1, 1) == 7000);
                    menu.setCarried(ItemStack.EMPTY);
                }
                case 89 -> {
                    var menu = (WorldSimulatorMenu) player.containerMenu;
                    check("menu_slots", menu.slots.size() == 50);
                    var upgrades = menu.getSlots(appeng.menu.SlotSemantics.UPGRADE);
                    check("native_upgrade_slots", upgrades.size() == 4
                            && upgrades.getFirst().mayPlace(AEItems.SPEED_CARD.stack())
                            && upgrades.getFirst().mayPlace(AEItems.CAPACITY_CARD.stack())
                            && !upgrades.getFirst().mayPlace(Items.STICK.getDefaultInstance()));
                    for (var face : appeng.api.orientation.RelativeSide.values()) {
                        Direction unrotated = face.getUnrotatedSide();
                        int bit = 1 << unrotated.ordinal();
                        int outputs = MACHINES[1].exportFaces();
                        boolean mapping = relative(1, menu.worldSide(face)) == unrotated;
                        boolean toggled = menu.clickMenuButton(player, unrotated.ordinal())
                                && MACHINES[1].exportFaces() == (outputs ^ bit)
                                && menu.clickMenuButton(player, unrotated.ordinal())
                                && MACHINES[1].exportFaces() == outputs;
                        check("face_output_toggle_" + face.name().toLowerCase(java.util.Locale.ROOT), mapping && toggled);
                    }
                    Direction west = relative(1, Direction.WEST);
                    int westBit = 1 << west.ordinal();
                    check("six_face_controls", menu.clickMenuButton(player, west.ordinal())
                            && (MACHINES[1].exportFaces() & westBit) == 0);
                    int enabledFaces = MACHINES[1].exportFaces();
                    check("native_active_output_exactly_two_states",
                            !MACHINES[1].activeOutput()
                            && menu.clickMenuButton(player, WorldSimulatorMenu.TOGGLE_ACTIVE_OUTPUT) && MACHINES[1].activeOutput()
                            && menu.clickMenuButton(player, WorldSimulatorMenu.TOGGLE_ACTIVE_OUTPUT) && !MACHINES[1].activeOutput()
                            && MACHINES[1].exportFaces() == enabledFaces);
                }
                case 102 -> {
                    check("resumes_after_drain", output(1).getCount() == 18
                            && fluidAmount(1, 0) == 0 && fluidAmount(1, 1) == 8000);
                    check("passive_output_extraction", drain(1, Direction.EAST, Fluids.WATER, 1000) == 1000);
                    var menu = (WorldSimulatorMenu) player.containerMenu;
                    menu.setCarried(new ItemStack(Items.WATER_BUCKET));
                    check("menu_fill_container", menu.clickMenuButton(player, WorldSimulatorMenu.FILL_INPUT)
                            && menu.getCarried().is(Items.BUCKET) && fluidAmount(1, 0) == 1000);
                    menu.setCarried(ItemStack.EMPTY);
                    verifyFluidClearAndPassivePorts(menu);
                }
                case 110 -> {
                    insert(1, 0, "ae2:charged_certus_quartz_crystal", 32);
                    insert(1, 1, "minecraft:redstone", 32);
                    insert(1, 2, "minecraft:quartz", 32);
                    // Neighboring enabled and disabled output buttons remain distinguishable at 1x.
                    MACHINES[1].toggleOutputFace(relative(1, Direction.UP));
                    ProbeState.screenshotRequest = MENU_FRAME;
                }
                case 120, 130, 140, 150, 160 -> {
                    drain(1, Direction.EAST, Fluids.WATER, 1000);
                    fill(1, Direction.DOWN, Fluids.WATER, 1000);
                }
                case 170 -> controlSetup();
                case 196 -> controlTurnOn();
                case 202 -> controlInverted();
                case 208 -> controlUnpower();
                case 216 -> {
                    check("powered_adjacent_cable_and_drive_exclude_simulator", checkIndependentCable());
                    check("redstone_inverted_runs_without_signal", MACHINES[9].progress()
                            > (int) REPORT.get("redstoneProgressBeforeInversion"));
                    MACHINES[9].cycleRedstone();
                    check("redstone_cycle_returns_always", MACHINES[9].redstoneMode() == 0);
                    finish(true, null);
                }
                default -> {}
            }
        } catch (Throwable error) {
            finish(false, error);
        }
    }


    private static Direction relative(int machine, Direction side) {
        return MACHINES[machine].getOrientation().getRelativeSide(side).getUnrotatedSide();
    }

    private static void verifyFluidClearAndPassivePorts(WorldSimulatorMenu menu) throws ReflectiveOperationException {
        var machine = MACHINES[1];
        var registry = level.registryAccess();
        var snapshot = machine.saveCustomOnly(registry);
        var legacy = snapshot.copy();
        legacy.putInt("inputFaces", 0);
        legacy.remove("activeOutput");
        legacy.remove("activeMode");
        legacy.putBoolean("autoExport", true);
        legacy.putBoolean("exportToMe", true);
        var restored = new WorldSimulatorBlockEntity(machine.getBlockPos(), machine.getBlockState());
        restored.loadCustomOnly(legacy, registry);
        check("legacy_export_mask_and_storage_preserved", restored.exportFaces() == machine.exportFaces()
                && restored.activeOutput() && !restored.saveCustomOnly(registry).contains("exportToMe")
                && restored.tankContents(0).equals(machine.tankContents(0))
                && restored.tankContents(1).equals(machine.tankContents(1)));

        for (int oldMode = 0; oldMode < 4; oldMode++) {
            legacy.putInt("activeMode", oldMode);
            restored.loadCustomOnly(legacy, registry);
            check("legacy_active_mode_" + oldMode + "_to_output_switch", restored.activeOutput() == (oldMode >= 2)
                    && restored.exportFaces() == machine.exportFaces());
        }

        long in = fluidAmount(1, 0), out = fluidAmount(1, 1);
        check("single_right_click_never_clears", menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_INPUT)
                && fluidAmount(1, 0) == in && fluidAmount(1, 1) == out);
        check("cross_tank_right_click_never_clears", menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_OUTPUT)
                && fluidAmount(1, 0) == in && fluidAmount(1, 1) == out);
        menu.clickMenuButton(player, WorldSimulatorMenu.CANCEL_CLEAR);
        check("cancelled_gesture_does_not_clear", menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_OUTPUT)
                && fluidAmount(1, 1) == out);
        menu.clickMenuButton(player, WorldSimulatorMenu.DRAIN_OUTPUT);
        check("left_interruption_does_not_clear", menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_OUTPUT)
                && fluidAmount(1, 1) == out);
        var start = WorldSimulatorMenu.class.getDeclaredField("clearStartedAt");
        if (!start.trySetAccessible()) throw new IllegalAccessException("clearStartedAt");
        start.setLong(menu, System.nanoTime() - 1_000_000_000L);
        check("expired_right_click_does_not_clear", menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_OUTPUT)
                && fluidAmount(1, 1) == out);
        menu.setCarried(new ItemStack(Items.BUCKET));
        check("held_container_prevents_destructive_clear", !menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_OUTPUT)
                && fluidAmount(1, 1) == out);
        menu.setCarried(ItemStack.EMPTY);
        check("double_right_clears_only_input", menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_INPUT)
                && menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_INPUT)
                && fluidAmount(1, 0) == 0 && fluidAmount(1, 1) == out);
        check("seed_passive_item_and_fluid_ports", fill(1, Direction.WEST, Fluids.LAVA, 1000) == 1000);
        insert(1, 8, "minecraft:stick", 1);
        for (var face : Direction.values()) {
            var bit = 1 << relative(1, face).ordinal();
            if ((machine.exportFaces() & bit) != 0) machine.toggleOutputFace(relative(1, face));
            check("passive_ports_with_output_disabled_" + face.getName(), passiveFaceWithContents(face));
        }
        check("double_right_clears_only_output", menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_OUTPUT)
                && menu.clickMenuButton(player, WorldSimulatorMenu.CLEAR_OUTPUT)
                && fluidAmount(1, 0) == 1006 && fluidAmount(1, 1) == 0);
        check("fluid_input_is_not_extractable", drain(1, Direction.WEST, Fluids.LAVA, 1) == 0);
        machine.loadCustomOnly(snapshot, registry);
    }

    private static boolean passiveFaceWithContents(Direction side) {
        var pos = MACHINES[1].getBlockPos();
        var items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        var fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        var lava = new FluidStack(Fluids.LAVA, 1);
        var water = new FluidStack(Fluids.WATER, 1);
        int input = count(1, 8), output = count(1, 9);
        long tankIn = fluidAmount(1, 0), tankOut = fluidAmount(1, 1);
        var correctOutput = output(1).copyWithCount(1);
        boolean blocked = items.extractItem(8, 1, false).isEmpty()
                && !items.insertItem(9, correctOutput, false).isEmpty()
                && !fluids.isFluidValid(1, water)
                && fluids.drain(lava, IFluidHandler.FluidAction.EXECUTE).isEmpty();
        boolean unchanged = count(1, 8) == input && count(1, 9) == output
                && fluidAmount(1, 0) == tankIn && fluidAmount(1, 1) == tankOut;
        var itemOut = items.extractItem(9, 1, false);
        var fluidOut = fluids.drain(water, IFluidHandler.FluidAction.EXECUTE);
        boolean good = blocked && unchanged && itemOut.getCount() == 1 && ItemStack.isSameItemSameComponents(itemOut, correctOutput)
                && items.insertItem(8, Items.STICK.getDefaultInstance(), false).isEmpty()
                && fluids.fill(lava, IFluidHandler.FluidAction.EXECUTE) == 1 && fluidOut.getAmount() == 1
                && fluidOut.getFluid() == Fluids.WATER;
        return good && count(1, 8) == input + 1 && count(1, 9) == output - 1
                && fluidAmount(1, 0) == tankIn + 1 && fluidAmount(1, 1) == tankOut - 1;
    }

    private static void controlSetup() {
        var red = MACHINES[9];
        red.cycleRedstone();
        check("redstone_signal_only_selected", red.redstoneMode() == 1);
        insert(9, 0, "ae2:singularity", 1);
        insert(9, 1, "ae2:ender_dust", 1);
        insert(9, 2, "minecraft:tnt", 1);

        var receiver = MACHINES[10];
        var source = MACHINES[11];
        receiver.toggleActiveOutput();
        source.toggleOutputFace(relative(11, Direction.WEST));
        source.toggleActiveOutput();
        check("peer_active_output_selected", source.activeOutput() && receiver.activeOutput());
        insert(11, 0, "ae2:charged_certus_quartz_crystal", 1);
        insert(11, 1, "minecraft:redstone", 1);
        insert(11, 2, "minecraft:quartz", 1);
        check("peer_recipe_initial_fluid", fill(11, Direction.UP, Fluids.WATER, 1000) == 1000);
    }

    private static void controlTurnOn() {
        check("redstone_off_without_signal", MACHINES[9].progress() == 0 && output(9).isEmpty());
        check("closed_active_output_keeps_item_and_fluid", output(11).getCount() == 2
                && fluidAmount(11, 1) == 1000 && count(10, 0) == 0 && fluidAmount(10, 0) == 0);
        var pos = MACHINES[9].getBlockPos().below();
        level.setBlockAndUpdate(pos, Blocks.REDSTONE_BLOCK.defaultBlockState());
        check("real_neighbor_signal", level.hasNeighborSignal(MACHINES[9].getBlockPos()));
        check("output_enabled_receiver_never_pulls", receivedPeerItems() == 0 && fluidAmount(10, 0) == 0);
        var source = MACHINES[11];
        source.toggleOutputFace(relative(11, Direction.WEST));
        int enabled = source.exportFaces();
        source.toggleActiveOutput();
        check("global_output_off_does_not_erase_faces", !source.activeOutput() && source.exportFaces() == enabled);
    }

    private static void controlInverted() {
        int progress = MACHINES[9].progress();
        check("redstone_on_with_signal", progress > 0 && progress < 20 && output(9).isEmpty());
        REPORT.put("redstoneProgressBeforeInversion", progress);
        MACHINES[9].cycleRedstone();
        check("redstone_signal_disable_selected", MACHINES[9].redstoneMode() == 2);
        var source = MACHINES[11];
        check("global_off_prevents_both_exports_and_neighbor_does_not_pull",
                receivedPeerItems() == 0 && fluidAmount(10, 0) == 0
                && output(11).is(AEItems.FLUIX_CRYSTAL.asItem()) && output(11).getCount() == 2 && fluidAmount(11, 1) == 1000);
        source.toggleActiveOutput();
    }

    private static int receivedPeerItems() {
        int items = 0;
        for (int i = 0; i < WorldSimulatorBlockEntity.INPUT_SLOTS; i++) {
            var stack = MACHINES[10].getInternalInventory().getStackInSlot(i);
            if (!stack.isEmpty() && !stack.is(AEItems.FLUIX_CRYSTAL.asItem())) return -1;
            items += stack.getCount();
        }
        return items;
    }

    private static void controlUnpower() {
        check("turning_output_back_on_resumes_items_and_fluid", receivedPeerItems() == 2
                && fluidAmount(10, 0) == 1000 && output(11).isEmpty() && fluidAmount(11, 1) == 0);

        check("redstone_inverted_stops_with_signal", MACHINES[9].progress()
                == (int) REPORT.get("redstoneProgressBeforeInversion"));
        level.setBlockAndUpdate(MACHINES[9].getBlockPos().below(), Blocks.SMOOTH_STONE.defaultBlockState());
    }

    private static void setup() {
        for (int index = 0; index < MACHINES.length; index++) {
            BlockPos pos = ORIGIN.offset(index == 11 ? 31 : index * 3, 0, 0);
            level.setBlockAndUpdate(pos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, ModBlocks.WORLD_SIMULATOR.get().defaultBlockState());
            MACHINES[index] = (WorldSimulatorBlockEntity) level.getBlockEntity(pos);
        }

        var reference = ORIGIN.west(2);
        level.setBlockAndUpdate(reference.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        level.setBlockAndUpdate(reference, ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get().defaultBlockState());

        insert(0, 0, "ae2:singularity", 2);
        insert(0, 1, "ae2:ender_dust", 2);
        insert(0, 2, "minecraft:tnt", 1);
        insert(6, 0, "ae2:singularity", 1);
        insert(6, 1, "ae2:ender_dust", 1);
        insert(6, 2, "ae2:tiny_tnt", 1);
        var west = MACHINES[6].getBlockPos().west();
        var east = MACHINES[6].getBlockPos().east();
        level.setBlockAndUpdate(west, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(east, Blocks.CHEST.defaultBlockState());
        MACHINES[6].toggleOutputFace(relative(6, Direction.WEST));
        MACHINES[6].toggleActiveOutput();

        insert(1, 0, "ae2:charged_certus_quartz_crystal", 9);
        insert(1, 1, "minecraft:redstone", 9);
        insert(1, 2, "minecraft:quartz", 9);
        check("capacity_8000mB", fill(1, Direction.UP, Fluids.WATER, 9000) == 8000);

        insert(2, 0, "ae2lpt:256m_loop_storage_core", 63);
        insert(2, 1, "ae2lpt:loop_storage_cell_housing", 1);
        insert(2, 2, "minecraft:tnt", 1);
        insert(3, 0, "justdirethings:time_crystal", 3);
        check("fill_jdt", fill(3, Direction.NORTH, fluid("justdirethings:polymorphic_fluid_source"), 4000) == 4000);

        insert(4, 0, "ae2:chipped_budding_quartz", 2);
        insert(4, 1, "ae2:quartz_block", 8);
        insert(4, 2, "ae2:fluix_block", 8);
        insert(4, 3, "minecraft:lightning_rod", 1);

        insert(5, 0, "ae2lt:lightning_collapse_matrix", 1);
        insert(5, 1, "ae2lt:overload_tnt", 1);
        for (Direction side : Direction.values()) {
            check("item_and_fluid_port_" + side.getName(), portCheck(side));
        }
        checkShapedRecipe();
        setupUpgrades();
        setupStandaloneSourceAndSink();
        var studioBlock = ModBlocks.WORLD_SIMULATOR.get();
        level.setBlockAndUpdate(EXTERIOR_POS, studioBlock.getOrientationStrategy().setOrientation(
                studioBlock.defaultBlockState(), Direction.NORTH, Direction.UP));
        var studio = (WorldSimulatorBlockEntity) level.getBlockEntity(EXTERIOR_POS);
        check("studio_block_north_front_all_faces_clear",
                studio.getOrientation().getSide(appeng.api.orientation.RelativeSide.FRONT) == Direction.NORTH
                && java.util.Arrays.stream(Direction.values()).allMatch(d -> level.isEmptyBlock(EXTERIOR_POS.relative(d))));
    }

    /** Refill only the isolated photography fixture, through its real input API on the server thread. */
    static void maintainExteriorRecipe(MinecraftServer server) {
        var studioLevel = server.overworld();
        if (!(studioLevel.getBlockEntity(EXTERIOR_POS) instanceof WorldSimulatorBlockEntity studio)
                || studioLevel.hasNeighborSignal(EXTERIOR_POS)) return;
        var inventory = studio.getInternalInventory();
        if (!inventory.getStackInSlot(0).isEmpty()) return;
        inventory.setItemDirect(WorldSimulatorBlockEntity.INPUT_SLOTS, ItemStack.EMPTY);
        studio.insertInput(0, new ItemStack(item("ae2:singularity")), false);
        studio.insertInput(1, new ItemStack(item("ae2:ender_dust")), false);
        studio.insertInput(2, new ItemStack(Items.TNT), false);
    }

    private static void setupUpgrades() {
        var machine = MACHINES[7];
        int[] cycles = {20, 10, 5, 3, 1};
        for (int slot = 0; slot < 4; slot++) {
            check("cycle_ticks_" + slot, machine.cycleTicks() == cycles[slot]);
            machine.upgrades().setItemDirect(slot, AEItems.SPEED_CARD.stack());
        }
        check("cycle_ticks_4", machine.cycleTicks() == cycles[4]);
        for (int slot = 0; slot < 4; slot++) {
            machine.upgrades().setItemDirect(slot, AEItems.CAPACITY_CARD.stack());
            check("capacity_multiplier_" + (slot + 1), machine.capacityMultiplier() == (2 << slot)
                    && machine.tankCapacity() == (16000 << slot));
        }
        insert(7, 0, "ae2:charged_certus_quartz_crystal", 40);
        insert(7, 1, "minecraft:redstone", 40);
        insert(7, 2, "minecraft:quartz", 40);
        insert(7, 3, "minecraft:stone", 64);
        insert(7, 3, "minecraft:stone", 64);
        check("bulk_fluid_fill", fill(7, Direction.SOUTH, Fluids.WATER, 40000) == 40000);
    }

    private static void setupStandaloneSourceAndSink() {
        var east = MACHINES[8].getBlockPos().east();
        level.setBlockAndUpdate(east, ModBlocks.WORLD_SIMULATOR.get().defaultBlockState());
        recipient = (WorldSimulatorBlockEntity) level.getBlockEntity(east);
        for (var face : Direction.values()) {
            if (face != Direction.EAST) MACHINES[8].toggleOutputFace(relative(8, face));
        }
        check("only_east_allowed_at_two_resource_push_test",
                MACHINES[8].exportFaces() == 1 << relative(8, Direction.EAST).ordinal());
        insert(8, 0, "ae2:charged_certus_quartz_crystal", 1);
        insert(8, 1, "minecraft:redstone", 1);
        insert(8, 2, "minecraft:quartz", 1);
        check("seed_unpowered_immersion", fill(8, Direction.SOUTH, Fluids.WATER, 1000) == 1000);
    }

    private static int receivedItems() {
        int count = 0;
        for (int i = 0; i < WorldSimulatorBlockEntity.INPUT_SLOTS; i++) {
            var stack = recipient.getInternalInventory().getStackInSlot(i);
            if (!stack.isEmpty() && !stack.is(AEItems.FLUIX_CRYSTAL.asItem())) return -1;
            count += stack.getCount();
        }
        return count;
    }

    private static long receivedFluid() {
        var contents = recipient.tankContents(0);
        if (contents == null) return 0;
        return contents.what().equals(AEFluidKey.of(Fluids.WATER)) ? contents.amount() : -1;
    }

    private static void observeOneTransferCycle() {
        int arrived = receivedItems(), remains = output(8).getCount();
        long liquidArrived = receivedFluid(), liquidRemains = fluidAmount(8, 1);
        if ((arrived != 0 || liquidArrived != 0) && simultaneousTick < 0) {
            check("both_resources_first_arrive_same_server_tick", arrived == 2 && liquidArrived == 1000
                    && remains == 0 && liquidRemains == 0 && level.getGameTime() % 5 == 0);
            simultaneousTick = level.getGameTime();
        }
        if (arrived + remains != 2 || liquidArrived + liquidRemains != 1000
                || !recipient.getInternalInventory().getStackInSlot(WorldSimulatorBlockEntity.INPUT_SLOTS).isEmpty()
                || recipient.tankContents(1) != null) {
            throw new AssertionError("Output path violated exact two-resource conservation or wrote to a recipient output");
        }
    }

    private static boolean hasNoNetworkOrPower(WorldSimulatorBlockEntity machine, boolean noNeighbors) {
        if ((Object) machine instanceof appeng.api.networking.IInWorldGridNodeHost) return false;
        var pos = machine.getBlockPos();
        if (level.getCapability(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null) != null) return false;
        for (var face : Direction.values()) {
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face) != null) return false;
            if (noNeighbors && (level.getCapability(appeng.api.AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    pos.relative(face), null) != null
                    || level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(face), face.getOpposite()) != null)) return false;
        }
        return true;
    }

    private static void placePoweredIndependentCable() {
        var cable = MACHINES[8].getBlockPos().north();
        var drive = cable.north();
        var battery = cable.below();
        level.setBlockAndUpdate(battery, AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
        ((appeng.blockentity.networking.EnergyCellBlockEntity) level.getBlockEntity(battery))
                .injectAEPower(50000, Actionable.MODULATE);
        appeng.api.parts.PartHelper.setPart(level, cable, null, player,
                appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT));
        level.setBlockAndUpdate(drive, AEBlocks.DRIVE.block().defaultBlockState());
        ((DriveBlockEntity) level.getBlockEntity(drive)).getInternalInventory()
                .setItemDirect(0, ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());
    }

    private static boolean checkIndependentCable() {
        var machine = MACHINES[8];
        var cablePos = machine.getBlockPos().north();
        var cable = appeng.api.parts.PartHelper.getPart(level, cablePos, null);
        var drive = (DriveBlockEntity) level.getBlockEntity(cablePos.north());
        var cableNode = cable == null ? null : cable.getGridNode();
        var driveNode = drive == null ? null : drive.getMainNode().getNode();
        boolean isolated = hasNoNetworkOrPower(machine, false);
        boolean driveActive = driveNode != null && driveNode.isActive();
        boolean sameGrid = cableNode != null && driveNode != null && cableNode.getGrid() == driveNode.getGrid();
        boolean southConnected = cableNode != null && cableNode.getConnections().stream()
                .anyMatch(connection -> connection.getDirection(cableNode) == Direction.SOUTH);
        REPORT.put("adjacentPoweredGrid", java.util.Map.of("isolated", isolated, "driveActive", driveActive,
                "sameGrid", sameGrid, "cableNode", cableNode != null, "driveNode", driveNode != null,
                "cableSouthConnected", southConnected));
        return isolated && driveActive && sameGrid && !southConnected;
    }

    private static void checkDirectionalExport() {
        var west = (ChestBlockEntity) level.getBlockEntity(MACHINES[6].getBlockPos().west());
        var east = (ChestBlockEntity) level.getBlockEntity(MACHINES[6].getBlockPos().east());
        REPORT.put("directionalOutputCounts", List.of(west.getItem(0).getCount(), east.getItem(0).getCount(), output(6).getCount()));
        check("tiny_tnt_and_directional_auto_export", west.isEmpty() && east.getItem(0).getCount() == 2
                && east.getItem(0).is(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem()) && output(6).isEmpty());
    }

    private static void firstBatch() {
        check("expands_item_and_fluid_output", output(7).getCount() == 80 && fluidAmount(7, 1) == 40000
                && count(7, 3) == 128);
        check("vanilla_tnt_explosion", output(0).is(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem())
                && output(0).getCount() == 2 && count(0, 0) == 1 && count(0, 1) == 1 && count(0, 2) == 0
                && !output(0).getComponentsPatch().isEmpty());
        check("immersion_max_batch_and_return", output(1).is(AEItems.FLUIX_CRYSTAL.asItem())
                && output(1).getCount() == 16 && fluidAmount(1, 0) == 0 && fluidAmount(1, 1) == 8000);
        REPORT.put("jdtFluids", List.of(fluidAmount(3, 0), fluidAmount(3, 1)));
        check("jdt_conversion", output(3).isEmpty() && count(3, 0) == 0 && fluidAmount(3, 0) == 1000
                && fluidAmount(3, 1) == 3000
                && MACHINES[3].tankContents(1).what().equals(AEFluidKey.of(fluid("justdirethings:time_fluid_source"))));
        check("lightning_strike", output(4).is(item("ae2lt:damaged_budding_overload_crystal"))
                && output(4).getCount() == 2 && count(4, 1) == 0 && count(4, 2) == 0 && count(4, 3) == 1);
        check("overload_tnt", output(5).is(item("ae2lt:mysterious_cell")) && output(5).getCount() == 1
                && count(5, 0) == 0 && count(5, 1) == 0 && !output(5).getComponentsPatch().isEmpty());
    }

    private static int count(int machine, int slot) {
        return MACHINES[machine].getInternalInventory().getStackInSlot(slot).getCount();
    }

    private static ItemStack output(int machine) {
        return MACHINES[machine].getInternalInventory().getStackInSlot(WorldSimulatorBlockEntity.INPUT_SLOTS);
    }

    private static long fluidAmount(int machine, int tank) {
        var stack = MACHINES[machine].tankContents(tank);
        return stack == null ? 0 : stack.amount();
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new IllegalArgumentException(id));
    }

    private static Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new IllegalArgumentException(id));
    }

    private static void checkShapedRecipe() {
        Item[] items = {Items.IRON_INGOT, ModItems.LOOP_CRYSTAL.get(), Items.IRON_INGOT, item("ae2:formation_plane"),
                item("ae2lpt:4k_loop_storage_core"), item("ae2:annihilation_plane"),
                Items.IRON_INGOT, item("ae2:cell_workbench"), Items.IRON_INGOT};
        var stacks = java.util.Arrays.stream(items).map(Item::getDefaultInstance).toList();
        var holder = level.getServer().getRecipeManager().getRecipes().stream()
                .filter(r -> r.id().toString().equals("ae2lpt:loop_world_simulator")).findFirst().orElseThrow();
        var recipe = (CraftingRecipe) holder.value();
        var input = CraftingInput.of(3, 3, stacks);
        check("shaped_recipe", recipe.matches(input, level)
                && recipe.assemble(input, level.registryAccess()).is(ModBlocks.WORLD_SIMULATOR.get().asItem()));
    }

    private static void insert(int machine, int slot, String id, int count) {
        var pos = MACHINES[machine].getBlockPos();
        var port = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.SOUTH);
        if (!port.insertItem(slot, new ItemStack(item(id), count), false).isEmpty()) {
            throw new IllegalStateException("Fixture insertion was rejected: " + id);
        }
    }

    private static int fill(int machine, Direction side, Fluid fluid, int amount) {
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, MACHINES[machine].getBlockPos(), side);
        return port.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    private static int drain(int machine, Direction side, Fluid fluid, int amount) {
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, MACHINES[machine].getBlockPos(), side);
        return port.drain(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
    }

    private static boolean portCheck(Direction side) {
        var pos = MACHINES[1].getBlockPos();
        var items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        var fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        return items.getSlots() == 10 && !items.isItemValid(9, Items.STICK.getDefaultInstance())
                && items.extractItem(0, 1, true).isEmpty()
                && fluids.getTankCapacity(0) == 8000 && fluids.getTankCapacity(1) == 8000
                && !fluids.isFluidValid(1, new FluidStack(Fluids.WATER, 1))
                && fluids.drain(1, IFluidHandler.FluidAction.SIMULATE).isEmpty();
    }

    private static void check(String name, boolean success) {
        REPORT.put(name, success);
        if (!success) {
            throw new AssertionError(name);
        }
    }

    private static void finish(boolean passed, Throwable failure) {
        REPORT.put("passed", passed);
        if (failure != null) {
            REPORT.put("error", failure.toString());
            failure.printStackTrace();
        }
        try {
            Files.writeString(output.resolve("report.json"), new com.google.gson.GsonBuilder().setPrettyPrinting()
                    .create().toJson(REPORT));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        } finally {
            ProbeState.finished = true;
        }
    }
}
