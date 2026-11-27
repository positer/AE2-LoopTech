package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.world.WorldRecipeCatalog;
import java.util.List;
import net.minecraft.client.Minecraft;

final class WorldSimulatorRecipeViews {
    private WorldSimulatorRecipeViews() {}

    static List<WorldRecipeCatalog.Display> get() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of();
        }
        var recipes = appeng.client.AppEngClient.instance().getRecipeMapForType(level, appeng.recipes.AERecipeTypes.TRANSFORM);
        return WorldRecipeCatalog.displays(recipes.values(), level.registryAccess());
    }
}
