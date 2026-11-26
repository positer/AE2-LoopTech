package com.example.ae2lightoptimizer.client;

import appeng.client.render.crafting.LightBakedModel;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

record LoopIntegratedCpuModel(boolean online) implements IUnbakedGeometry<LoopIntegratedCpuModel> {
    static void register(ModelEvent.RegisterGeometryLoaders event) {
        IGeometryLoader<LoopIntegratedCpuModel> loader =
                (json, context) -> new LoopIntegratedCpuModel(json.get("online").getAsBoolean());
        event.register(ResourceLocation.fromNamespaceAndPath("ae2lpt", "loop_integrated_cpu"), loader);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
            Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
        return new LightBakedModel(
                sprites.apply(texture("ring_corner")),
                sprites.apply(texture("ring_side_hor")),
                sprites.apply(texture("ring_side_ver")),
                sprites.apply(texture("light_base")),
                sprites.apply(texture(online ? "online" : "offline")));
    }

    private static Material texture(String name) {
        return new Material(TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.fromNamespaceAndPath("ae2lpt", "block/loop_integrated_cpu/" + name));
    }
}
