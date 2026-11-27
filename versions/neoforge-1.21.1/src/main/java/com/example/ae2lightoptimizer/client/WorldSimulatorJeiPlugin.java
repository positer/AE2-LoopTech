package com.example.ae2lightoptimizer.client;

import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.world.WorldRecipeCatalog.Display;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class WorldSimulatorJeiPlugin implements IModPlugin {
    private static final RecipeType<Display> TYPE = RecipeType.create("ae2lpt", "world_simulator", Display.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.parse("ae2lpt:world_simulator");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var recipes = WorldSimulatorRecipeViews.get();
        registration.addRecipes(TYPE, recipes);
        if (!recipes.isEmpty()) {
            com.mojang.logging.LogUtils.getLogger().debug("World simulator JEI recipes: {}", recipes.size());
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.WORLD_SIMULATOR.get().getDefaultInstance(), TYPE);
    }

    private static final class Category implements IRecipeCategory<Display> {
        private final IDrawable icon;
        private final IDrawable arrow;
        private final IDrawable slotBackground;

        Category(IGuiHelper gui) {
            icon = gui.createDrawableItemStack(ModItems.WORLD_SIMULATOR.get().getDefaultInstance());
            arrow = gui.getRecipeArrow();
            slotBackground = gui.getSlotDrawable();
        }

        @Override
        public RecipeType<Display> getRecipeType() {
            return TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("block.ae2lpt.loop_world_simulator");
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public int getWidth() {
            return 138;
        }

        @Override
        public int getHeight() {
            return 58;
        }

        @Override
        public ResourceLocation getRegistryName(Display recipe) {
            return ResourceLocation.parse("ae2lpt:world_simulator/" + recipe.id().getNamespace() + "/" + recipe.id().getPath());
        }

        @Override
        public void draw(Display recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            arrow.draw(graphics, 88, 21);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder layout, Display recipe, IFocusGroup focus) {
            for (int i = 0; i < recipe.items().size(); i++) {
                var input = recipe.items().get(i);
                var slot = layout.addSlot(input.retained() ? RecipeIngredientRole.CATALYST : RecipeIngredientRole.INPUT,
                        3 + (i % 3) * 18, 2 + (i / 3) * 18);
                slot.setBackground(slotBackground, -1, -1);
                slot.addItemStacks(input.alternatives().stream().map(item -> new ItemStack(item, input.count())).toList());
            }
            if (!recipe.fluidInputs().isEmpty()) {
                var slot = layout.addInputSlot(65, 20);
                slot.setBackground(slotBackground, -1, -1);
                slot.setFluidRenderer(1000, false, 16, 16);
                recipe.fluidInputs().forEach(fluid -> slot.addFluidStack(fluid, 1000));
            }
            if (!recipe.output().isEmpty()) {
                var slot = layout.addOutputSlot(119, recipe.fluidOutputs().isEmpty() ? 20 : 11);
                slot.setBackground(slotBackground, -1, -1);
                slot.addItemStack(recipe.output());
            }
            if (!recipe.fluidOutputs().isEmpty()) {
                var slot = layout.addOutputSlot(119, recipe.output().isEmpty() ? 20 : 29);
                slot.setBackground(slotBackground, -1, -1);
                slot.setFluidRenderer(1000, false, 16, 16);
                recipe.fluidOutputs().forEach(fluid -> slot.addFluidStack(fluid, 1000));
            }
        }
    }
}
