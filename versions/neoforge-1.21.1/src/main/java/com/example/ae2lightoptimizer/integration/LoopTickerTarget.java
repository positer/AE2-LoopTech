package com.example.ae2lightoptimizer.integration;

import appeng.api.parts.IPartHost;
import com.example.ae2lightoptimizer.api.LoopTickerHost;
import com.example.ae2lightoptimizer.block.LoopTickerBlockEntity;
import com.example.ae2lightoptimizer.part.LoopTickerPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;

public final class LoopTickerTarget {
    // Protect a server tick even if a branching arrangement would multiply millions of calls.
    private static final LoopTickerCascade<LoopTickerHost> CASCADE = new LoopTickerCascade<>(8192);
    private static final Direction[] SIDES = Direction.values();

    private LoopTickerTarget() {}

    public static void accelerate(LoopTickerHost source, ServerLevel server, BlockPos target, int times) {
        CASCADE.run(source, times, () -> tickTarget(server, target));
    }

    private static boolean tickTarget(ServerLevel server, BlockPos target) {
        if (!server.hasChunkAt(target) || !server.shouldTickBlocksAt(target)) return false;
        var state = server.getBlockState(target);
        if (state.isAir()) return false;
        var entity = server.getBlockEntity(target);
        if (entity instanceof LoopTickerBlockEntity ticker && !entity.isRemoved()) {
            ticker.accelerate();
            return true;
        }
        if (entity instanceof IPartHost host && !entity.isRemoved()) {
            boolean hasTicker = false;
            // Queue in reverse: the iterative cascade executes the top frame first.
            for (int i = SIDES.length - 1; i >= 0; i--) {
                if (host.getPart(SIDES[i]) instanceof LoopTickerPart ticker) {
                    hasTicker = true;
                    ticker.accelerateFromTicker();
                }
            }
            if (hasTicker) return true;
        }
        if (entity != null && !entity.isRemoved() && state.getBlock() instanceof EntityBlock block) {
            @SuppressWarnings("unchecked")
            var ticker = (BlockEntityTicker<BlockEntity>) block.getTicker(server, state, entity.getType());
            if (ticker != null) ticker.tick(server, target, state, entity);
        }
        state = server.getBlockState(target);
        if (state.isRandomlyTicking()) state.randomTick(server, target, server.getRandom());
        return true;
    }
}
