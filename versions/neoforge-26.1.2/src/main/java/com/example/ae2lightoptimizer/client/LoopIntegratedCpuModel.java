package com.example.ae2lightoptimizer.client;

import appeng.client.render.crafting.LightBakedModel;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

record LoopIntegratedCpuModel(boolean online) implements CustomUnbakedBlockStateModel {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("ae2lpt", "loop_integrated_cpu");
    private static final MapCodec<LoopIntegratedCpuModel> CODEC =
            Codec.BOOL.fieldOf("online").xmap(LoopIntegratedCpuModel::new, LoopIntegratedCpuModel::online);

    static void register(RegisterBlockStateModels event) {
        event.registerModel(ID, CODEC);
    }

    @Override
    public BlockStateModel bake(ModelBaker baker) {
        var materials = baker.materials();
        return new LightBakedModel(
                materials.get(texture("ring_corner"), ID::toString),
                materials.get(texture("ring_side_hor"), ID::toString),
                materials.get(texture("ring_side_ver"), ID::toString),
                materials.get(texture("light_base"), ID::toString),
                materials.get(texture(online ? "online" : "offline"), ID::toString));
    }

    private static Material texture(String name) {
        return new Material(Identifier.fromNamespaceAndPath("ae2lpt", "block/loop_integrated_cpu/" + name));
    }

    @Override
    public void resolveDependencies(ResolvableModel.Resolver resolver) {
    }

    @Override
    public MapCodec<LoopIntegratedCpuModel> codec() {
        return CODEC;
    }
}
