package com.example.ae2lightoptimizer.world;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class WorldSimulatorBlock extends AEBaseEntityBlock<WorldSimulatorBlockEntity> {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public WorldSimulatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (getBlockEntity(level, pos) instanceof WorldSimulatorBlockEntity machine) {
            if (!level.isClientSide()) {
                MenuOpener.open(WorldSimulatorMenu.TYPE.get(), player, MenuLocators.forBlockEntity(machine));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
