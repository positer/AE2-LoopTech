package com.example.ae2lightoptimizer.world;

import com.example.ae2lightoptimizer.block.ModBlockEntities;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.item.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

public final class WorldSimulatorContent {
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue AUTO;
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ALLOW;
    private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DENY;
    private static volatile long configRevision;

    static {
        var builder = new ModConfigSpec.Builder();
        AUTO = builder.comment("Import all supported world recipes when the allow list is empty.")
                .define("automatic", true);
        ALLOW = builder.comment("Exact recipe IDs or namespace wildcards, e.g. ae2:*.")
                .defineListAllowEmpty("allow", java.util.List.of(), WorldSimulatorContent::validPattern);
        DENY = builder.comment("Exact recipe IDs or namespace wildcards. Deny overrides allow.")
                .defineListAllowEmpty("deny", java.util.List.of(), WorldSimulatorContent::validPattern);
        SPEC = builder.build();
    }

    private WorldSimulatorContent() {}

    public static void register(IEventBus bus, ModContainer container) {
        WorldSimulatorMenu.MENUS.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.WORLD_SIMULATOR.get(),
                    WorldSimulatorBlockEntity::getExposedItemHandler);
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.WORLD_SIMULATOR.get(),
                    WorldSimulatorBlockEntity::fluidPort);
        });
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            var type = ModBlockEntities.WORLD_SIMULATOR.get();
            ModBlocks.WORLD_SIMULATOR.get().setBlockEntity(
                    WorldSimulatorBlockEntity.class, type, null, WorldSimulatorBlockEntity::tick);
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.SPEED_CARD,
                    ModBlocks.WORLD_SIMULATOR.get(), WorldSimulatorBlockEntity.UPGRADE_SLOTS);
            appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.CAPACITY_CARD,
                    ModBlocks.WORLD_SIMULATOR.get(), WorldSimulatorBlockEntity.UPGRADE_SLOTS);
            appeng.blockentity.AEBaseBlockEntity.registerBlockEntityItem(type, ModItems.WORLD_SIMULATOR.get());
        }));
        bus.addListener((ModConfigEvent.Loading event) -> configChanged(event));
        bus.addListener((ModConfigEvent.Reloading event) -> configChanged(event));
        NeoForge.EVENT_BUS.addListener(WorldRecipeCatalog::invalidate);
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "ae2lpt-world-simulator.toml");
    }

    private static void configChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            configRevision++;
        }
    }

    static long configRevision() {
        return configRevision;
    }

    static boolean permits(String id, String type) {
        if (!SPEC.isLoaded()) {
            return true;
        }
        return DENY.get().stream().noneMatch(pattern -> matches(pattern, id, type))
                && ((AUTO.get() && ALLOW.get().isEmpty())
                    || ALLOW.get().stream().anyMatch(pattern -> matches(pattern, id, type)));
    }

    private static boolean validPattern(Object value) {
        return value instanceof String pattern
                && pattern.matches("[a-z0-9_.-]+:(?:[a-z0-9_./-]+|\\*)");
    }

    private static boolean matches(String pattern, String id, String type) {
        if (pattern.endsWith(":*")) {
            return id.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(id) || pattern.equals(type);
    }
}
