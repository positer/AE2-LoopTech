package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.world.WorldRecipeCatalog.Display;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@EmiEntrypoint
public final class WorldSimulatorEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        var machine = EmiStack.of(ModItems.WORLD_SIMULATOR.get());
        var type = new EmiRecipeCategory(ResourceLocation.parse("ae2lpt:world_simulator"), machine) {
            @Override
            public Component getName() {
                return Component.translatable("block.ae2lpt.loop_world_simulator");
            }
        };
        registry.addCategory(type);
        registry.addWorkstation(type, machine);
        var recipes = WorldSimulatorRecipeViews.get();
        for (var recipe : recipes) {
            registry.addRecipe(new View(type, recipe));
        }
        if (!recipes.isEmpty()) {
            com.mojang.logging.LogUtils.getLogger().debug("World simulator EMI recipes: {}", recipes.size());
        }
    }

    private static final class View extends BasicEmiRecipe {
        private final List<EmiIngredient> itemSlots = new ArrayList<>();
        private final List<EmiIngredient> retained = new ArrayList<>();
        private final EmiIngredient fluidInput;
        private final EmiIngredient fluidOutput;
        private final EmiStack itemOutput;

        View(EmiRecipeCategory type, Display recipe) {
            super(type, ResourceLocation.parse("ae2lpt:/world_simulator/" + recipe.id().getNamespace()
                    + "/" + recipe.id().getPath()), 138, 58);
            for (var input : recipe.items()) {
                var ingredient = EmiIngredient.of(input.alternatives().stream().map(EmiStack::of).toList(), input.count());
                itemSlots.add(ingredient);
                if (input.retained()) {
                    retained.add(ingredient);
                } else {
                    inputs.add(ingredient);
                }
            }
            fluidInput = EmiIngredient.of(recipe.fluidInputs().stream().map(fluid -> EmiStack.of(fluid, 1000)).toList());
            if (!fluidInput.isEmpty()) {
                inputs.add(fluidInput);
            }
            itemOutput = EmiStack.of(recipe.output());
            if (!itemOutput.isEmpty()) {
                outputs.add(itemOutput);
            }
            var fluids = recipe.fluidOutputs().stream().map(fluid -> EmiStack.of(fluid, 1000)).toList();
            fluidOutput = EmiIngredient.of(fluids);
            outputs.addAll(fluids);
        }

        @Override
        public List<EmiIngredient> getCatalysts() {
            return retained;
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            for (int i = 0; i < itemSlots.size(); i++) {
                var ingredient = itemSlots.get(i);
                var slot = widgets.addSlot(ingredient, 2 + 18 * (i % 3), 1 + 18 * (i / 3));
                if (retained.contains(ingredient)) {
                    slot.catalyst(true);
                }
            }
            widgets.addFillingArrow(88, 20, 1000);
            if (!fluidInput.isEmpty()) {
                widgets.addSlot(fluidInput, 64, 19);
            }
            if (!itemOutput.isEmpty()) {
                widgets.addSlot(itemOutput, 118, fluidOutput.isEmpty() ? 19 : 10).recipeContext(this);
            }
            if (!fluidOutput.isEmpty()) {
                widgets.addSlot(fluidOutput, 118, itemOutput.isEmpty() ? 19 : 28).recipeContext(this);
            }
        }
    }
}
