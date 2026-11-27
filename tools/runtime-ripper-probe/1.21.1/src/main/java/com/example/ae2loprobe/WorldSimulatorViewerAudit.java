package com.example.ae2loprobe;

import com.example.ae2lightoptimizer.world.WorldRecipeCatalog.Display;
import dev.emi.emi.api.EmiApi;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

final class WorldSimulatorViewerAudit {
    private static boolean finished;

    private WorldSimulatorViewerAudit() {}

    static boolean check(Path evidence, boolean timedOut) {
        if (finished) {
            return true;
        }
        var result = new LinkedHashMap<String, Object>();
        boolean complete = true;
        if (ModList.get().isLoaded("jei")) {
            List<String> names = jeiRecipes();
            result.put("JEI", names);
            complete &= names.size() >= 6;
        }
        if (ModList.get().isLoaded("emi")) {
            var manager = EmiApi.getRecipeManager();
            var names = manager.getRecipes().stream()
                    .filter(recipe -> recipe.getCategory().getId().toString().equals("ae2lpt:world_simulator"))
                    .map(recipe -> recipe.getId().toString()).sorted().toList();
            result.put("EMI", names);
            complete &= names.size() >= 6;
        }
        if (complete || timedOut) {
            try {
                Files.writeString(evidence.resolve("world-simulator-viewers.json"),
                        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
                finished = true;
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return finished;
    }

    private static List<String> jeiRecipes() {
        try {
            var getRuntime = Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime");
            var runtime = (IJeiRuntime) getRuntime.invoke(null);
            if (runtime == null) {
                return List.of();
            }
            var manager = runtime.getRecipeManager();
            var type = manager.getRecipeType(ResourceLocation.parse("ae2lpt:world_simulator"), Display.class).orElse(null);
            if (type == null) {
                return List.of();
            }
            return manager.createRecipeLookup(type).get().map(display -> display.id().toString()).sorted().toList();
        } catch (java.lang.reflect.InvocationTargetException pending) {
            return List.of();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot read the JEI recipe registry", ex);
        }
    }
}
