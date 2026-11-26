package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import com.example.ae2lightoptimizer.api.LoopTickerHost;
import com.example.ae2lightoptimizer.block.LoopIntegratedCpuBlockEntity;
import com.example.ae2lightoptimizer.block.LoopTickerBlockEntity;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.integration.LoopTickerPower;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

final class IntegratedCpuFixture {
    static final boolean ENABLED = "integrated_cpu".equals(System.getProperty("ae2lo.probe.chain"));
    private static final BlockPos CPU = new BlockPos(0, 101, 0);
    private static final BlockPos OFFLINE = CPU.offset(-3, 0, 0);
    private static final BlockPos INVALID = CPU.offset(0, 0, -4);
    private static final BlockPos DRIVE = CPU.east();
    private static final BlockPos PROVIDER = DRIVE.east();
    private static final BlockPos EXTENDED = CPU.offset(2, 0, 3);
    private static final BlockPos BLOCK_TICKER = CPU.offset(-4, 0, 5);
    private static final BlockPos PART_TICKER = CPU.offset(5, 0, 5);
    private static final Direction INPUT = Direction.NORTH;
    private static final String WORLD_FRAME = "ae2lo-ripper-integrated-cpu-world.png";
    static final String CPU_FRAME = "ae2lo-ripper-integrated-cpu-menu.png";
    static final String DRIVE_FRAME = "ae2lo-ripper-integrated-cpu-reference-drive.png";
    static final String TICKER_FRAME = "ae2lo-ripper-integrated-cpu-ticker-menu.png";
    private static final LinkedHashMap<String, Object> report = new LinkedHashMap<>();
    private static ServerLevel level;
    private static ServerPlayer player;
    private static Path output;
    private static LoopIntegratedCpuBlockEntity cpu;
    private static LoopTickerBlockEntity blockTicker;
    private static com.example.ae2lightoptimizer.part.LoopTickerPart partTicker;
    private static CraftingCPUCluster cluster;
    private static Future<ICraftingPlan> plan;
    private static int ticks;
    private static long appliedFluxReceived;

    static void start(MinecraftServer server, ServerPlayer tester, Path directory) {
        level = server.overworld();
        player = tester;
        output = directory;
        report.put("minecraft", "26.1.2");
        report.put("chain", "integrated_cpu");
        report.put("world", server.getWorldData().getLevelName());
        try {
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.connection.teleport(0.5, 103, 4.5, 180, 30);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) level.setChunkForced(x, z, true);
            }
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 100, z), Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }
            var chassis = ModBlocks.LOOP_INTEGRATED_CPU.get().defaultBlockState();
            for (var pos : List.of(CPU, CPU.west(), OFFLINE, INVALID, INVALID.east(), INVALID.north())) {
                level.setBlockAndUpdate(pos, chassis);
            }
            cpu = (LoopIntegratedCpuBlockEntity) level.getBlockEntity(CPU);
            level.setBlockAndUpdate(DRIVE, AEBlocks.DRIVE.block().defaultBlockState());
            level.setBlockAndUpdate(DRIVE.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            level.setBlockAndUpdate(PROVIDER, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
            level.setBlockAndUpdate(PROVIDER.east(), AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
            var extDrive = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("extendedae", "ex_drive"))
                    .orElseThrow();
            level.setBlockAndUpdate(EXTENDED, extDrive.defaultBlockState());
            ((DriveBlockEntity) level.getBlockEntity(DRIVE)).getInternalInventory()
                    .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            var ticker = ModBlocks.LOOP_TICKER.get();
            level.setBlockAndUpdate(BLOCK_TICKER, ticker.getOrientationStrategy().setFacing(ticker.defaultBlockState(), INPUT));
            blockTicker = (LoopTickerBlockEntity) level.getBlockEntity(BLOCK_TICKER);
            level.setBlockAndUpdate(PART_TICKER, AEBlocks.CABLE_BUS.block().defaultBlockState());
            var host = (IPartHost) level.getBlockEntity(PART_TICKER);
            host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), INPUT, null);
            partTicker = host.addPart(ModItems.LOOP_TICKER_PART.get(), INPUT, null);
            for (var pos : List.of(BLOCK_TICKER, PART_TICKER)) {
                level.setBlockAndUpdate(pos.below(), AEBlocks.DENSE_ENERGY_CELL.block().defaultBlockState());
                level.setBlockAndUpdate(pos.relative(INPUT), Blocks.FURNACE.defaultBlockState());
                var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos.relative(INPUT));
                furnace.setItem(0, new ItemStack(Items.RAW_IRON, 16));
                furnace.setItem(1, new ItemStack(Items.COAL, 8));
            }
            ProbeState.phase = "cpu_grid_start";
        } catch (Exception error) {
            finish(error);
        }
    }

    static void tick() {
        if (output == null || ProbeState.finished) return;
        try {
            if (++ticks > 1200) throw new IllegalStateException("Timed out: " + ProbeState.phase);
            switch (ProbeState.phase) {
                case "cpu_grid_start" -> {
                    if (ticks < 60) return;
                    check(cpu.isActive() && cpu.getCluster() != null, "empty CPU chassis must form and connect");
                    cluster = cpu.getCluster();
                    check(cluster.getAvailableStorage() == 0, "empty CPU advertised storage");
                    check(cluster.getGrid().getCraftingService().getCpus().contains(cluster),
                            "AE2 crafting service did not list the all-integrated CPU");
                    check(((LoopIntegratedCpuBlockEntity) level.getBlockEntity(OFFLINE)).isFormed()
                            && !((LoopIntegratedCpuBlockEntity) level.getBlockEntity(OFFLINE)).isPowered(), "offline state");
                    check(!((LoopIntegratedCpuBlockEntity) level.getBlockEntity(INVALID)).isFormed(), "invalid shape formed");
                    checkCpuAndRecipe();
                    checkFe(BLOCK_TICKER, blockTicker);
                    checkFe(PART_TICKER, partTicker);
                    ProbeState.screenshotRequest = WORLD_FRAME;
                    ProbeState.phase = "cpu_world_frame";
                }
                case "cpu_world_frame" -> {
                    if (!WORLD_FRAME.equals(ProbeState.screenshotCompleted)) return;
                    open(CPU);
                    ProbeState.screenshotRequest = CPU_FRAME;
                    ProbeState.phase = "cpu_menu_frame";
                }
                case "cpu_menu_frame" -> {
                    if (!CPU_FRAME.equals(ProbeState.screenshotCompleted)) return;
                    var reference = (DriveBlockEntity) level.getBlockEntity(EXTENDED);
                    for (int slot = 0; slot < cpu.cells().size(); slot++) {
                        reference.getInternalInventory().setItemDirect(slot, cpu.cells().getStackInSlot(slot).copy());
                    }
                    open(EXTENDED);
                    ProbeState.screenshotRequest = DRIVE_FRAME;
                    ProbeState.phase = "reference_drive_frame";
                }
                case "reference_drive_frame" -> {
                    if (!DRIVE_FRAME.equals(ProbeState.screenshotCompleted)) return;
                    open(BLOCK_TICKER);
                    ProbeState.screenshotRequest = TICKER_FRAME;
                    ProbeState.phase = "ticker_frame";
                }
                case "ticker_frame" -> {
                    if (!TICKER_FRAME.equals(ProbeState.screenshotCompleted)) return;
                    player.closeContainer();
                    var grid = cluster.getGrid();
                    var source = new MachineSource(grid::getPivot);
                    plan = grid.getCraftingService().beginCraftingCalculation(level, () -> source,
                            AEItemKey.of(Items.OAK_PLANKS), 32, CalculationStrategy.REPORT_MISSING_ITEMS);
                    ProbeState.phase = "cpu_native_plan";
                }
                case "cpu_native_plan" -> {
                    if (!plan.isDone()) return;
                    var order = plan.get();
                    check(!order.simulation(), "native CPU plan is missing materials: " + order.missingItems());
                    cpu.cells().setItemDirect(18, ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance());
                    check(cluster.getAvailableStorage() == Long.MAX_VALUE && cluster.getCoProcessors() == Integer.MAX_VALUE - 1,
                            "finite disks changed infinite capacity");
                    var grid = cluster.getGrid();
                    check(grid.getCraftingService().submitJob(order, null, null, false,
                            new MachineSource(grid::getPivot)).successful(), "AE2 auto-selection rejected the native CPU job");
                    check(cluster.isBusy(), "native CPU did not acquire job");
                    cpu.cells().setItemDirect(1, ItemStack.EMPTY);
                    check(cpu.getCluster() == cluster && cluster.isBusy(), "live disk removal replaced/broke active job");
                    report.put("nativePlanBytes", order.bytes());
                    report.put("nativeAutoSelectedCpu", true);
                    ProbeState.phase = "cpu_native_job";
                }
                case "cpu_native_job" -> {
                    if (cluster.isBusy()) return;
                    var inv = cluster.getGrid().getStorageService().getInventory();
                    check(inv.getAvailableStacks().get(AEItemKey.of(Items.OAK_PLANKS)) == 32, "native CPU output");
                    check(inv.getAvailableStacks().get(AEItemKey.of(Items.OAK_LOG)) == 8, "native CPU material debit");
                    cpu.cells().setItemDirect(18, ItemStack.EMPTY);
                    check(cpu.getCluster() == cluster && cluster.getAvailableStorage() == 6144
                            && cluster.getCoProcessors() == 8, "finite totals did not return after the infinite job");
                    report.put("nativePlanksCrafted", 32);
                    report.put("infiniteNativeOrderCompleted", true);
                    report.put("feAccepted", appliedFluxReceived);
                    for (var pos : List.of(BLOCK_TICKER, PART_TICKER)) {
                        var furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos.relative(INPUT));
                        int outputCount = furnace.getItem(2).getCount();
                        check(outputCount > 0, "FE-powered furnace acceleration produced no output");
                        report.put(pos.equals(BLOCK_TICKER) ? "blockFurnaceOutput" : "partFurnaceOutput", outputCount);
                    }
                    check(blockTicker.getMainNode().isActive() && partTicker.getMainNode().isActive(),
                            "one FE-powered ticker did not become a functioning network member");
                    finish(null);
                }
            }
        } catch (Exception error) {
            finish(error);
        }
    }

    private static void checkCpuAndRecipe() {
        var infinite = ModItems.INFINITE_LOOP_STORAGE_CELL.get().getDefaultInstance();
        cpu.cells().setItemDirect(18, infinite);
        check(cluster.getAvailableStorage() == Long.MAX_VALUE, "single infinite disk storage");
        check(cluster.getCoProcessors() == Integer.MAX_VALUE - 1, "single infinite disk co-processors");
        report.put("infiniteStorageBytes", cluster.getAvailableStorage());
        report.put("infiniteCoprocessors", cluster.getCoProcessors());
        cpu.cells().setItemDirect(17, infinite.copy());
        check(cluster.getAvailableStorage() == Long.MAX_VALUE
                && cluster.getCoProcessors() == Integer.MAX_VALUE - 1, "two infinite disks overflowed");
        cpu.cells().setItemDirect(17, ItemStack.EMPTY);
        cpu.cells().setItemDirect(18, ItemStack.EMPTY);
        check(cpu.getCluster() == cluster && cluster.getAvailableStorage() == 0 && cluster.getCoProcessors() == 0,
                "removing all infinite disks did not restore the original empty CPU");
        report.put("infiniteDisksHotPlugged", true);
        var cell256k = ModItems.LOOP_STORAGE_CELL_256K.get();
        var cell1m = ModItems.LOOP_STORAGE_CELL_1M.get();
        check(cell256k.tier().hasTypeLimit() && cell256k.tier().maxTypes() == 63
                && cell256k.tier().capacityBytes() == 262144, "256k disk storage contract");
        check(!cell1m.tier().hasTypeLimit() && cell1m.tier().capacityBytes() == 66060288,
                "1M disk storage pool and type contract");
        cpu.cells().setItemDirect(18, cell256k.getDefaultInstance());
        long bytes256k = cluster.getAvailableStorage();
        int co256k = cluster.getCoProcessors();
        check(bytes256k == 262144 && co256k == 1024, "256k Loop cell CPU contribution");
        cpu.cells().setItemDirect(18, cell1m.getDefaultInstance());
        long bytes1m = cluster.getAvailableStorage();
        int co1m = cluster.getCoProcessors();
        check(bytes1m == 4 * bytes256k && co1m == 4 * co256k, "1M Loop cell CPU contribution");
        report.put("loop256kCpuBytes", bytes256k);
        report.put("loop256kCpuCoprocessors", co256k);
        report.put("loop1mCpuBytes", bytes1m);
        report.put("loop1mCpuCoprocessors", co1m);
        report.put("loop1mDiskPoolBytes", cell1m.tier().capacityBytes());
        cpu.cells().setItemDirect(18, ItemStack.EMPTY);
        cpu.cells().setItemDirect(0, AEItems.ITEM_CELL_4K.stack());
        check(cluster.getAvailableStorage() == 4096 && cluster.getCoProcessors() == 0, "item disk contribution");
        cpu.cells().setItemDirect(1, AEItems.FLUID_CELL_256K.stack());
        check(cluster.getAvailableStorage() == 4096 && cluster.getCoProcessors() == 1024, "fluid disk contribution");
        cpu.cells().setItemDirect(10, ModItems.LOOP_STORAGE_CELL_1K.get().getDefaultInstance());
        check(cluster.getAvailableStorage() == 5120 && cluster.getCoProcessors() == 1028, "loop disk contribution");
        var quantum = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("ae2omnicells", "quantum_omni_cell_1k"));
        if (quantum.isPresent()) {
            cpu.cells().setItemDirect(19, quantum.orElseThrow().getDefaultInstance());
            check(cluster.getAvailableStorage() == 6144 && cluster.getCoProcessors() == 1032, "quantum disk contribution");
        }
        report.put("quantumDiskPresent", quantum.isPresent());
        report.put("bytesWithCells", cluster.getAvailableStorage());
        report.put("coprocessorsWithCells", cluster.getCoProcessors());
        var iron = new ItemStack(Items.IRON_INGOT);
        var crystal = ModItems.LOOP_CRYSTAL.get().getDefaultInstance();
        var recipe = CraftingInput.of(3, 3, List.of(iron, crystal, iron, crystal, AEBlocks.CRAFTING_UNIT.stack(),
                crystal, iron, AEBlocks.DRIVE.stack(), iron));
        var manager = level.getServer().getRecipeManager();
        var result = manager.getRecipeFor(RecipeType.CRAFTING, recipe, level).orElseThrow().value()
                .assemble(recipe);
        check(result.is(ModItems.LOOP_INTEGRATED_CPU.get()), "shaped CPU recipe result");
        var log = new ItemStack(Items.OAK_LOG);
        var input = CraftingInput.of(1, 1, List.of(log));
        var holder = manager.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        var planks = holder.value().assemble(input);
        var patternGrid = new ItemStack[9];
        java.util.Arrays.fill(patternGrid, ItemStack.EMPTY);
        patternGrid[0] = log;
        var encoded = PatternDetailsHelper.encodeCraftingPattern(holder, patternGrid, planks, false, false);
        var provider = ((PatternProviderBlockEntity) level.getBlockEntity(PROVIDER)).getLogic();
        provider.getPatternInv().setItemDirect(0, encoded);
        check(provider.getAvailablePatterns().size() == 1, "native provider rejected the encoded plank pattern");
        var grid = cluster.getGrid();
        check(grid.getStorageService().getInventory().insert(AEItemKey.of(log), 16, Actionable.MODULATE,
                new MachineSource(grid::getPivot)) == 16, "ME drive insert failed");
    }

    private static void checkFe(BlockPos pos, LoopTickerHost host) {
        var cap = Capabilities.Energy.BLOCK;
        check(level.getCapability(cap, pos, INPUT) == null, "FE port available without card");
        var card = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("appflux", "induction_card")).orElseThrow();
        var upgrades = host.getUpgrades();
        check(LoopTickerPower.cardAvailable() && upgrades.getMaxInstalled(card) == 1, "induction limit");
        check(upgrades.isItemValid(LoopTickerHost.INDUCTION_SLOT, card.getDefaultInstance()), "induction card invalid");
        upgrades.setItemDirect(LoopTickerHost.INDUCTION_SLOT, card.getDefaultInstance());
        check(!upgrades.isItemValid(0, card.getDefaultInstance()), "second induction card accepted");
        check(host.maxRate() == 4, "induction card counted as speed card");
        for (var side : Direction.values()) {
            if (side != INPUT) check(level.getCapability(cap, pos, side) == null, "wrong FE input side: " + side);
        }
        check(level.getCapability(cap, pos, null) == null, "unsided input accepted");
        var port = level.getCapability(cap, pos, INPUT);
        check(port != null, "missing FE port at " + pos);
        var grid = (host instanceof LoopTickerBlockEntity block ? block.getMainNode() : partTicker.getMainNode()).getGrid();
        var network = grid.getEnergyService();
        double previous = network.extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.ONE);
        int simulated = receive(port, Integer.MAX_VALUE, true);
        check(simulated > 0 && network.extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.ONE) == previous, "FE simulation mutated AE power");
        int real = receive(port, Integer.MAX_VALUE, false);
        double stored = network.extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.ONE);
        check(real == simulated && stored > previous, "simulated/committed FE mismatch: " + real
                + "/" + simulated + " AE " + previous + " -> " + stored);
        report.put(host.getClass().getSimpleName() + "AddedAe", stored - previous);
        appliedFluxReceived += real;
        check(receive(port, Integer.MAX_VALUE, false) == 0, "full AE grid rejected surplus FE incorrectly");
        upgrades.setItemDirect(LoopTickerHost.INDUCTION_SLOT, ItemStack.EMPTY);
        check(level.getCapability(cap, pos, INPUT) == null && receive(port, 1, false) == 0, "stale FE port after card removal");
        upgrades.setItemDirect(LoopTickerHost.INDUCTION_SLOT, card.getDefaultInstance());
        report.put(host.getClass().getSimpleName() + "Fe", real);
    }

    private static int receive(net.neoforged.neoforge.transfer.energy.EnergyHandler port, int amount, boolean simulate) {
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int accepted = port.insert(amount, tx);
            if (!simulate) tx.commit();
            return accepted;
        }
    }

    private static void open(BlockPos pos) {
        player.closeContainer();
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        level.getBlockState(pos).useWithoutItem(level, player, hit);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new IllegalStateException(description);
    }

    private static void finish(Exception failure) {
        report.put("passed", failure == null);
        if (failure != null) {
            report.put("failure", failure.toString());
            failure.printStackTrace();
        }
        report.put("cpuCodeSource", LoopIntegratedCpuBlockEntity.class.getProtectionDomain().getCodeSource().getLocation().toString());
        try {
            Files.writeString(output.resolve("report.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
        ProbeState.phase = failure == null ? "passed" : "failed";
        ProbeState.finished = true;
    }
}
