/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.service;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.core.link.CustomBlockLook;
import art.arcane.iris.core.link.CustomBlockSource;
import art.arcane.iris.core.link.ExternalDataProvider;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.link.data.DataType;
import art.arcane.iris.core.nms.container.BlockProperty;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.common.plugin.IrisService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ExternalDataSVC implements IrisService, CustomBlockSource {
    private static final String PROVIDER_PACKAGE = "art.arcane.iris.core.link.data.";
    private static final List<ProviderDefinition> BUILT_IN_PROVIDERS = List.of(
            new ProviderDefinition("CraftEngine", PROVIDER_PACKAGE + "CraftEngineDataProvider"),
            new ProviderDefinition("Nexo", PROVIDER_PACKAGE + "NexoDataProvider"),
            new ProviderDefinition("ItemsAdder", PROVIDER_PACKAGE + "ItemAdderDataProvider"),
            new ProviderDefinition("ExecutableItems", PROVIDER_PACKAGE + "ExecutableItemsDataProvider"),
            new ProviderDefinition("MMOItems", PROVIDER_PACKAGE + "MMOItemsDataProvider"),
            new ProviderDefinition("EcoItems", PROVIDER_PACKAGE + "EcoItemsDataProvider"),
            new ProviderDefinition("MythicMobs", PROVIDER_PACKAGE + "MythicMobsDataProvider"),
            new ProviderDefinition("MythicCrucible", PROVIDER_PACKAGE + "MythicCrucibleDataProvider"),
            new ProviderDefinition("KGenerators", PROVIDER_PACKAGE + "KGeneratorsDataProvider")
    );

    private final KList<ExternalDataProvider> providers = new KList<>();
    private final KList<ExternalDataProvider> activeProviders = new KList<>();
    private final AtomicBoolean placeholdersHandedOut = new AtomicBoolean();

    /**
     * Whether a key that no provider claims may still belong to a provider that has not loaded its
     * content; if so, the caller gets a placeholder via {@link #placeholderWhileProvidersLoad()}.
     */
    public boolean isAwaitingProviderContent() {
        return activeProviders.stream().anyMatch(ExternalDataProvider::isLoadingContent);
    }

    /** Records that a block was resolved to a placeholder, so the engines using it reload once providers have loaded. */
    public void placeholderWhileProvidersLoad() {
        placeholdersHandedOut.set(true);
    }

    /** Reloads the Iris worlds that resolved a block while a provider was still loading, now that it has loaded. */
    public void onProviderContentLoaded(ExternalDataProvider provider) {
        if (isAwaitingProviderContent() || !placeholdersHandedOut.getAndSet(false)) {
            return;
        }
        J.s(() -> {
            List<Engine> engines = runningEngines();
            IrisLogging.info("%s loaded its blocks; reloading %d Iris world(s) that used them.",
                    provider.getPluginId(), engines.size());
            J.a(() -> engines.forEach(ExternalDataSVC::reloadAfterProviderContent));
        });
    }

    private static List<Engine> runningEngines() {
        return Bukkit.getWorlds().stream()
                .filter(IrisToolbelt::isIrisWorld)
                .map(IrisToolbelt::access)
                .filter(Objects::nonNull)
                .map(PlatformChunkGenerator::getEngine)
                .filter(Objects::nonNull)
                .toList();
    }

    private static void reloadAfterProviderContent(Engine engine) {
        try {
            engine.hotloadSilently();
        } catch (RuntimeException failure) {
            IrisLogging.reportError("Could not reload Iris world " + engine.getWorld().name()
                    + " after its plugin blocks loaded.", failure);
        }
    }

    @Override
    public void onEnable() {
        IrisLogging.debug("Loading ExternalDataProvider...");
        // enable() registers every enabled service as a listener; self-registration here
        // doubled every handler invocation.

        for (ProviderDefinition definition : BUILT_IN_PROVIDERS) {
            activateConfiguredProvider(definition);
        }
    }

    @Override
    public void onDisable() {
        activeProviders.clear();
        providers.clear();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        String pluginId = event.getPlugin().getName();
        Optional<ExternalDataProvider> existingProvider = findProvider(pluginId);
        if (existingProvider.isPresent()) {
            activateProvider(existingProvider.get());
            return;
        }

        BUILT_IN_PROVIDERS.stream()
                .filter(definition -> definition.pluginId().equalsIgnoreCase(pluginId))
                .findFirst()
                .ifPresent(this::activateConfiguredProvider);
    }

    public void registerProvider(ExternalDataProvider provider) {
        ExternalDataProvider registeredProvider = Objects.requireNonNull(provider);
        String pluginId = registeredProvider.getPluginId();
        boolean builtIn = BUILT_IN_PROVIDERS.stream()
                .anyMatch(definition -> definition.pluginId().equalsIgnoreCase(pluginId));
        if (builtIn || findProvider(pluginId).isPresent()) {
            throw new IllegalArgumentException("A provider with the same plugin id already exists.");
        }

        providers.add(registeredProvider);
        if (registeredProvider.isReady()) {
            activateProvider(registeredProvider);
        }
    }

    public Optional<BlockData> getBlockData(final Identifier key) {
        Pair<Identifier, KMap<String, String>> pair;
        try {
            pair = parseState(key);
        } catch (IllegalArgumentException e) {
            IrisLogging.error(e.getMessage());
            return Optional.empty();
        }
        Identifier mod = pair.getA();

        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(mod, DataType.BLOCK)).findFirst();
        if (provider.isEmpty())
            return Optional.empty();
        try {
            return Optional.of(provider.get().getBlockData(mod, pair.getB()));
        } catch (MissingResourceException e) {
            IrisLogging.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return Optional.empty();
        }
    }

    @Override
    public List<CustomBlockLook> blocks() {
        return activeProviders.stream()
                .flatMap(provider -> provider.getCustomBlocks().stream())
                .toList();
    }

    @Override
    public List<Path> resourcePacks() {
        return activeProviders.stream()
                .map(ExternalDataProvider::getResourcePack)
                .flatMap(Optional::stream)
                .toList();
    }

    public Optional<List<BlockProperty>> getBlockProperties(final Identifier key) {
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(key, DataType.BLOCK)).findFirst();
        if (provider.isEmpty())
            return Optional.empty();
        try {
            return Optional.of(provider.get().getBlockProperties(key));
        } catch (MissingResourceException e) {
            IrisLogging.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return Optional.empty();
        }
    }

    public Optional<ItemStack> getItemStack(Identifier key, KMap<String, Object> customNbt) {
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(key, DataType.ITEM)).findFirst();
        if (provider.isEmpty()) {
            IrisLogging.warnOnce("external-provider:item:" + key,
                    "No matching Provider found for modded material \"%s\"!", key);
            return Optional.empty();
        }
        try {
            return Optional.of(provider.get().getItemStack(key, customNbt));
        } catch (MissingResourceException e) {
            IrisLogging.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return Optional.empty();
        }
    }

    /** The most damage the custom item {@code key} takes before it breaks, 0 if it cannot be damaged; empty when no provider has the item. */
    public OptionalInt maxDurability(Identifier key) {
        Optional<ItemStack> stack = getItemStack(key, null);
        return stack.isEmpty() ? OptionalInt.empty() : OptionalInt.of(stack.get().getType().getMaxDurability());
    }

    /** The block made to never decay by the provider it belongs to, or {@code data} itself. */
    public BlockData withoutDecay(BlockData data) {
        for (ExternalDataProvider provider : activeProviders) {
            BlockData kept = provider.withoutDecay(data);
            if (kept != data) {
                return kept;
            }
        }
        return data;
    }

    /** Whether {@code data} is a provider's block carrying {@code tag}. */
    public boolean hasBlockTag(BlockData data, NamespacedKey tag) {
        return activeProviders.stream().anyMatch(provider -> provider.hasBlockTag(data, tag));
    }

    /** Places the provider block {@code blockId}, which may carry a state like {@code ns:chair[yaw=90]}. */
    public void processUpdate(Engine engine, Block block, Identifier blockId) {
        Identifier baseBlockId = withoutState(blockId);
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(baseBlockId, DataType.BLOCK)).findFirst();
        if (provider.isEmpty()) {
            IrisLogging.warnOnce("external-provider:block:" + blockId,
                    "No matching Provider found for modded material \"%s\"!", blockId);
            return;
        }
        provider.get().processUpdate(engine, block, blockId);
    }

    public Entity spawnMob(Location location, Identifier mobId) {
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(mobId, DataType.ENTITY)).findFirst();
        if (provider.isEmpty()) {
            IrisLogging.warnOnce("external-provider:mob:" + mobId,
                    "No matching Provider found for modded mob \"%s\"!", mobId);
            return null;
        }
        try {
            return provider.get().spawnMob(location, mobId);
        } catch (MissingResourceException e) {
            IrisLogging.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return null;
        }
    }

    public Collection<Identifier> getAllIdentifiers(DataType dataType) {
        return activeProviders.stream()
                .flatMap(p -> p.getTypes(dataType).stream())
                .toList();
    }

    public Collection<Pair<Identifier, List<BlockProperty>>> getAllBlockProperties() {
        return activeProviders.stream()
                .flatMap(p -> p.getTypes(DataType.BLOCK)
                        .stream()
                        .map(id -> new Pair<>(id, p.getBlockProperties(id))))
                .toList();
    }

    static Identifier withoutState(Identifier key) {
        int open = key.key().indexOf('[');
        return open < 0 ? key : new Identifier(key.namespace(), key.key().substring(0, open));
    }

    public static Pair<Identifier, KMap<String, String>> parseState(Identifier key) {
        String raw = key.key();
        int open = raw.indexOf('[');
        int close = raw.lastIndexOf(']');
        if (open < 0 || close < open) {
            return new Pair<>(key, new KMap<>());
        }

        String state = raw.substring(open + 1, close);
        KMap<String, String> stateMap = new KMap<>();
        if (!state.isEmpty()) {
            for (String entry : state.split(",")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                    throw new IllegalArgumentException("Malformed block state \"" + entry + "\" in \"" + key + "\" (expected key=value)");
                }
                stateMap.put(pair[0], pair[1]);
            }
        }
        return new Pair<>(new Identifier(key.namespace(), raw.substring(0, open)), stateMap);
    }

    public static Identifier buildState(Identifier key, KMap<String, String> state) {
        if (state.isEmpty()) {
            return key;
        }
        String path = state.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(",", key.key() + "[", "]"));
        return new Identifier(key.namespace(), path);
    }

    private Optional<ExternalDataProvider> findProvider(String pluginId) {
        return providers.stream()
                .filter(provider -> provider.getPluginId().equalsIgnoreCase(pluginId))
                .findFirst();
    }

    private void activateConfiguredProvider(ProviderDefinition definition) {
        if (!isPluginEnabled(definition.pluginId()) || findProvider(definition.pluginId()).isPresent()) {
            return;
        }

        try {
            Class<?> rawProviderClass = Class.forName(definition.className(), true, ExternalDataSVC.class.getClassLoader());
            Class<? extends ExternalDataProvider> providerClass = rawProviderClass.asSubclass(ExternalDataProvider.class);
            ExternalDataProvider provider = providerClass.getDeclaredConstructor().newInstance();
            IrisLogging.debug(definition.pluginId() + " found, loading " + providerClass.getSimpleName() + "...");
            activateProvider(provider);
        } catch (Throwable error) {
            IrisLogging.reportError("Failed to create Iris external data provider " + definition.className() + ".", error);
        }
    }

    private void activateProvider(ExternalDataProvider provider) {
        if (activeProviders.contains(provider)) {
            return;
        }
        try {
            provider.init();
            if (provider instanceof Listener listener) {
                BukkitPlatform.volmitPlugin().registerListener(listener);
            }
            if (!providers.contains(provider)) {
                providers.add(provider);
            }
            activeProviders.add(provider);
            IrisLogging.debug("Enabled ExternalDataProvider for %s.", provider.getPluginId());
        } catch (Throwable error) {
            IrisLogging.reportError("Failed to enable Iris external data provider " + provider.getPluginId() + ".", error);
        }
    }

    private boolean isPluginEnabled(String pluginId) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginId);
        return plugin != null && plugin.isEnabled();
    }

    private record ProviderDefinition(String pluginId, String className) {
    }
}
