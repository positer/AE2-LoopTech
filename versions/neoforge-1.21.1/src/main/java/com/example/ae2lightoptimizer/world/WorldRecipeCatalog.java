package com.example.ae2lightoptimizer.world;

import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.recipes.transform.TransformRecipe;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;

public final class WorldRecipeCatalog {
    private static final Logger LOG = LogUtils.getLogger();
    private record Cached(long configRevision, List<Entry> entries) {}
    private static final Map<MinecraftServer, Cached> CACHE = new WeakHashMap<>();
    private static final TagKey<Item> EXPLOSIVES =
            TagKey.create(Registries.ITEM, ResourceLocation.parse("ae2lpt:world_simulator_explosives"));
    private static final int MAX_INPUT_ITEMS = 9 * 99 * 16;

    record Requirement(Predicate<ItemStack> matches, boolean consumed) {}
    record Plan(Entry recipe, BatchAllocation.Result items, FluidStack fluid) {}

    record Entry(ResourceLocation id, List<Requirement> requirements,
                 Predicate<Fluid> fluidInput, Fluid fluidOutput, boolean returnFluid,
                 ItemStack result, Consumer<ItemStack> prepareResult) {
        Plan plan(ItemStack[] inputs, ItemStack output, FluidStack incoming, FluidStack outgoing, int multiplier) {
            int limit = MAX_INPUT_ITEMS;
            if (!result.isEmpty()) {
                if (!output.isEmpty() && (prepareResult != null || !ItemStack.isSameItemSameComponents(output, result))) {
                    return null;
                }
                limit = (result.getMaxStackSize() * multiplier - output.getCount()) / result.getCount();
                if (prepareResult != null) {
                    limit = Math.min(limit, 1);
                }
            }
            FluidStack fluid = FluidStack.EMPTY;
            if (fluidInput != null) {
                if (incoming.isEmpty() || !fluidInput.test(incoming.getFluid())) {
                    return null;
                }
                fluid = returnFluid ? incoming.copyWithAmount(1000) : new FluidStack(fluidOutput, 1000);
                if (!outgoing.isEmpty() && !FluidStack.isSameFluidSameComponents(fluid, outgoing)) {
                    return null;
                }
                limit = Math.min(limit, Math.min(incoming.getAmount(),
                        WorldSimulatorBlockEntity.TANK_CAPACITY * multiplier - outgoing.getAmount()) / 1000);
            }
            if (limit <= 0 || requirements.size() > MAX_INPUT_ITEMS) {
                return null;
            }
            int[] stock = new int[inputs.length];
            int available = 0;
            for (int slot = 0; slot < stock.length; slot++) {
                stock[slot] = inputs[slot].getCount();
                available += stock[slot];
            }
            var bySlots = new TreeMap<Integer, int[]>();
            int consumed = 0;
            for (var requirement : requirements) {
                int mask = 0;
                for (int slot = 0; slot < inputs.length; slot++) {
                    if (stock[slot] > 0 && requirement.matches.test(inputs[slot])) {
                        mask |= 1 << slot;
                    }
                }
                if (mask == 0) {
                    return null;
                }
                bySlots.computeIfAbsent(mask, ignored -> new int[2])[requirement.consumed ? 0 : 1]++;
                if (requirement.consumed) {
                    consumed++;
                }
            }
            if (consumed > 0) {
                limit = Math.min(limit, (available - requirements.size() + consumed) / consumed);
            }
            var demands = bySlots.entrySet().stream().map(e ->
                    new BatchAllocation.Demand(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
            var allocation = BatchAllocation.maximum(stock, demands, limit);
            return allocation == null ? null : new Plan(this, allocation, fluid);
        }
    }

    static void invalidate(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            synchronized (CACHE) {
                CACHE.remove(event.getPlayerList().getServer());
            }
        }
    }

    static List<Entry> entries(MinecraftServer server) {
        synchronized (CACHE) {
            long revision = WorldSimulatorContent.configRevision();
            var cached = CACHE.get(server);
            if (cached == null || cached.configRevision != revision) {
                cached = new Cached(revision, discover(server.getRecipeManager().getRecipes(), server.registryAccess()));
                CACHE.put(server, cached);
            }
            return cached.entries;
        }
    }

    public static List<RecipeType<?>> supportedTypes() {
        var types = new ArrayList<RecipeType<?>>();
        for (String id : List.of("ae2:transform", "justdirethings:fluiddroprecipe", "ae2lt:lightning_strike")) {
            BuiltInRegistries.RECIPE_TYPE.getOptional(ResourceLocation.parse(id)).ifPresent(types::add);
        }
        return List.copyOf(types);
    }

    public record DisplayInput(List<Item> alternatives, boolean retained, int count) {}
    public record Display(ResourceLocation id, List<DisplayInput> items, List<Fluid> fluidInputs,
                          ItemStack output, List<Fluid> fluidOutputs) {}
    private record InputKey(List<Item> alternatives, boolean retained) {}

    public static List<Display> displays(Iterable<RecipeHolder<?>> recipes, RegistryAccess registries) {
        var items = new LinkedHashMap<Item, ItemStack>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                items.put(item, item.getDefaultInstance());
            }
        }
        var displays = new ArrayList<Display>();
        for (var entry : discover(recipes, registries)) {
            var requirements = new LinkedHashMap<InputKey, Integer>();
            for (var requirement : entry.requirements) {
                var alternatives = items.entrySet().stream()
                        .filter(item -> requirement.matches.test(item.getValue())).map(Map.Entry::getKey).toList();
                if (!alternatives.isEmpty()) {
                    requirements.merge(new InputKey(alternatives, !requirement.consumed), 1, Integer::sum);
                }
            }
            var displayInputs = requirements.entrySet().stream().map(e ->
                    new DisplayInput(e.getKey().alternatives, e.getKey().retained, e.getValue())).toList();
            var fluidInputs = new ArrayList<Fluid>();
            if (entry.fluidInput != null) {
                for (Fluid fluid : BuiltInRegistries.FLUID) {
                    if (fluid.defaultFluidState().isSource() && entry.fluidInput.test(fluid)) {
                        fluidInputs.add(fluid);
                    }
                }
            }
            List<Fluid> fluidOutputs = entry.returnFluid ? fluidInputs
                    : entry.fluidOutput != null ? List.of(entry.fluidOutput) : List.of();
            if (!displayInputs.isEmpty() && displayInputs.size() <= WorldSimulatorBlockEntity.INPUT_SLOTS) {
                displays.add(new Display(entry.id, displayInputs, List.copyOf(fluidInputs), entry.result,
                        List.copyOf(fluidOutputs)));
            }
        }
        return List.copyOf(displays);
    }

    private static List<Entry> discover(Iterable<RecipeHolder<?>> recipes, RegistryAccess registries) {
        var entries = new ArrayList<Entry>();
        for (var holder : recipes) {
            var id = holder.id();
            var recipe = holder.value();
            var type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (!WorldSimulatorContent.permits(id.toString(), type.toString())) {
                continue;
            }
            try {
                Entry entry = switch (type.toString()) {
                    case "ae2:transform" -> transform(id, (TransformRecipe) recipe);
                    case "justdirethings:fluiddroprecipe" -> fluidDrop(id, encode(recipe, registries), registries);
                    case "ae2lt:lightning_strike" -> lightning(id, encode(recipe, registries));
                    default -> null;
                };
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (RuntimeException ex) {
                LOG.warn("Cannot import world simulator recipe {}", id, ex);
            }
        }
        var special = ResourceLocation.parse("ae2lt:overload_tnt_mysterious_cell");
        if (ModList.get().isLoaded("ae2lt") && WorldSimulatorContent.permits(special.toString(), "ae2lt:overload_tnt")) {
            try {
                var entry = overload(special);
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                LOG.warn("Cannot import Lightning Tech overload TNT recipe", ex);
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.id.toString()));
        return List.copyOf(entries);
    }

    private static Entry transform(ResourceLocation id, TransformRecipe recipe) {
        var requirements = new ArrayList<Requirement>();
        // The raw field preserves the 64 repeated ingredients of the infinite cell.
        for (Ingredient ingredient : recipe.ingredients) {
            requirements.add(new Requirement(ingredient::test, true));
        }
        var circumstance = recipe.circumstance;
        var output = recipe.output.copy();
        if (requirements.isEmpty() || output.isEmpty()) {
            return null;
        }
        if (circumstance.isExplosion()) {
            requirements.add(new Requirement(stack -> stack.is(EXPLOSIVES), true));
            return new Entry(id, requirements, null, null, false, output,
                    AEItems.QUANTUM_ENTANGLED_SINGULARITY.is(output) && output.getCount() > 1
                            ? QuantumBridgeBlockEntity::assignFrequency : null);
        }
        return circumstance.isFluid() ? new Entry(id, requirements,
                fluid -> circumstance.isFluid(fluid.defaultFluidState()), null, true, output, null) : null;
    }

    @SuppressWarnings("unchecked")
    private static <R extends Recipe<?>> JsonObject encode(R recipe, RegistryAccess registries) {
        var codec = (com.mojang.serialization.MapCodec<R>) recipe.getSerializer().codec();
        return codec.codec().encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), recipe)
                .getOrThrow().getAsJsonObject();
    }

    private static Entry fluidDrop(ResourceLocation id, JsonObject json, RegistryAccess registries) {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var input = BlockState.CODEC.parse(ops, blockStateData(json.get("input"))).getOrThrow().getFluidState();
        var output = BlockState.CODEC.parse(ops, blockStateData(json.get("output"))).getOrThrow().getFluidState();
        if (!input.isSource() || !output.isSource()) {
            return null;
        }
        return new Entry(id, List.of(item(item(json.get("catalyst").getAsString()), true)),
                fluid -> fluid == input.getType(), output.getType(), false, ItemStack.EMPTY, null);
    }

    private static JsonElement blockStateData(JsonElement data) {
        if (data.isJsonObject() && data.getAsJsonObject().has("id")) {
            var source = data.getAsJsonObject();
            var vanilla = new JsonObject();
            vanilla.add("Name", source.get("id"));
            if (source.has("properties")) {
                vanilla.add("Properties", source.get("properties"));
            }
            return vanilla;
        }
        return data;
    }

    private static Entry lightning(ResourceLocation id, JsonObject json) {
        var requirements = new ArrayList<Requirement>();
        requirements.add(item(blockItem(json.get("center_input")), true));
        for (var element : json.getAsJsonArray("requirements")) {
            var requirement = element.getAsJsonObject();
            requirements.add(item(blockItem(requirement.get("block")),
                    !requirement.has("consume") || requirement.get("consume").getAsBoolean()));
        }
        requirements.add(item(Items.LIGHTNING_ROD, false));
        var result = blockItem(json.get("center_output")).getDefaultInstance();
        return new Entry(id, requirements, null, null, false, result, null);
    }

    private static Item blockItem(JsonElement id) {
        Block block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id.getAsString())).orElseThrow();
        if (block.asItem() == Items.AIR) {
            throw new IllegalArgumentException("Block has no item form: " + id);
        }
        return block.asItem();
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
    }

    private static Requirement item(Item item, boolean consumed) {
        return new Requirement(stack -> stack.is(item), consumed);
    }

    private static Entry overload(ResourceLocation id) throws ReflectiveOperationException {
        String trigger = "ae2lt:lightning_collapse_matrix";
        var config = Class.forName("com.moakiee.ae2lt.config.AE2LTCommonConfig");
        if (!(boolean) config.getMethod("easterEggEnabled").invoke(null)) {
            return null;
        }
        trigger = (String) config.getMethod("easterEggItem").invoke(null);
        var result = item("ae2lt:mysterious_cell").getDefaultInstance();
        var initialize = result.getItem().getClass().getMethod("initializeOuterCell", ItemStack.class);
        return new Entry(id, List.of(item(item(trigger), true), item(item("ae2lt:overload_tnt"), true)),
                null, null, false, result, stack -> {
                    try {
                        initialize.invoke(null, stack);
                    } catch (ReflectiveOperationException ex) {
                        throw new IllegalStateException("Lightning Tech could not initialize its mysterious cell", ex);
                    }
                });
    }
}
