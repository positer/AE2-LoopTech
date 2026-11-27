package com.example.ae2lightoptimizer.block;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.world.WorldSimulatorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Ae2LightOptimizer.NAMESPACE);

    public static final DeferredBlock<Block> RECIPE_RING_SOLVER_TERMINAL = BLOCKS.registerBlock(
            "recipe_ring_solver_terminal",
            RecipeRingSolverTerminalBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL));

    public static final DeferredBlock<Block> SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE = BLOCKS.registerBlock(
            "supercomputing_crafting_optimizer_interface",
            SupercomputingCraftingOptimizerInterfaceBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL));

    public static final DeferredBlock<CraftingRipperBlock> CRAFTING_RIPPER = BLOCKS.registerBlock(
            "crafting_ripper", CraftingRipperBlock::new,
            () -> BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL));

    public static final DeferredBlock<LoopCondenserBlock> LOOP_CONDENSER = BLOCKS.registerBlock(
            "loop_condenser", LoopCondenserBlock::new,
            () -> BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL));

    public static final DeferredBlock<LoopTickerBlock> LOOP_TICKER = BLOCKS.registerBlock(
            "loop_ticker", LoopTickerBlock::new,
            () -> BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL));

    public static final DeferredBlock<Block> LOOP_CRYSTAL_BLOCK = BLOCKS.registerBlock("loop_crystal_block", Block::new,
            () -> BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.AMETHYST));
    public static final DeferredBlock<LoopIntegratedCpuBlock> LOOP_INTEGRATED_CPU = BLOCKS.registerBlock(
            "loop_integrated_cpu", LoopIntegratedCpuBlock::new,
            () -> BlockBehaviour.Properties.of().strength(2.2F).sound(SoundType.METAL));

    public static final DeferredBlock<WorldSimulatorBlock> WORLD_SIMULATOR = BLOCKS.registerBlock(
            "loop_world_simulator", WorldSimulatorBlock::new,
            () -> BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).noOcclusion());

    private ModBlocks() {
    }
}