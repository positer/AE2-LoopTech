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
        return WorldRecipeCatalog.displays(level.getRecipeManager().getRecipes(), level.registryAccess());
    }
}
