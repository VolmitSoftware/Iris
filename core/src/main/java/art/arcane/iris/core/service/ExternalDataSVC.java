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
import art.arcane.iris.core.link.ExternalDataProvider;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.link.data.DataType;
import art.arcane.iris.core.nms.container.BlockProperty;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.util.common.data.IrisCustomData;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.iris.util.common.plugin.IrisService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class ExternalDataSVC implements IrisService {
    private static final String PROVIDER_PACKAGE = "art.arcane.iris.core.link.data.";
    private static final List<ProviderDefinition> BUILT_IN_PROVIDERS = List.of(
            new ProviderDefinition("CraftEngine", PROVIDER_PACKAGE + "CraftEngineDataProvider"),
            new ProviderDefinition("Nexo", PROVIDER_PACKAGE + "NexoDataProvider"),
            new ProviderDefinition("Oraxen", PROVIDER_PACKAGE + "OraxenDataProvider"),
            new ProviderDefinition("ItemsAdder", PROVIDER_PACKAGE + "ItemAdderDataProvider"),
            new ProviderDefinition("ExecutableItems", PROVIDER_PACKAGE + "ExecutableItemsDataProvider"),
            new ProviderDefinition("MMOItems", PROVIDER_PACKAGE + "MMOItemsDataProvider"),
            new ProviderDefinition("EcoItems", PROVIDER_PACKAGE + "EcoItemsDataProvider"),
            new ProviderDefinition("MythicMobs", PROVIDER_PACKAGE + "MythicMobsDataProvider"),
            new ProviderDefinition("MythicCrucible", PROVIDER_PACKAGE + "MythicCrucibleDataProvider"),
            new ProviderDefinition("KGenerators", PROVIDER_PACKAGE + "KGeneratorsDataProvider")
    );
    private static final Set<String> BLOCK_PROVIDER_PLUGINS = Set.of(
            "craftengine", "nexo", "oraxen", "itemsadder", "mmoitems", "mythiccrucible", "kgenerators");

    private final List<ExternalDataProvider> providers = new CopyOnWriteArrayList<>();
    private final List<ExternalDataProvider> activeProviders = new CopyOnWriteArrayList<>();
    private final AtomicLong contentRevision = new AtomicLong();
    private volatile Runnable contentChangeListener;

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
        contentChangeListener = null;
        activeProviders.clear();
        providers.clear();
    }

    public void setContentChangeListener(Runnable listener) {
        contentChangeListener = Objects.requireNonNull(listener);
    }

    public void notifyContentChanged() {
        contentRevision.incrementAndGet();
        Runnable listener = contentChangeListener;
        if (listener != null) {
            listener.run();
        }
    }

    public long contentRevision() {
        return contentRevision.get();
    }

    public boolean hasPendingBlockProvider(String blockKey) {
        Identifier identifier = Identifier.fromString(blockKey);
        if ("minecraft".equals(identifier.namespace())) {
            return false;
        }
        String namespace = identifier.namespace().toLowerCase(Locale.ROOT);
        Optional<ExternalDataProvider> qualified = findProvider(namespace);
        if (qualified.isPresent()) {
            return !qualified.get().isReady();
        }
        if (BLOCK_PROVIDER_PLUGINS.contains(namespace)) {
            return isInstalledProviderPending(namespace);
        }
        for (String pluginId : BLOCK_PROVIDER_PLUGINS) {
            if (isInstalledProviderPending(pluginId)) {
                return true;
            }
        }
        for (ExternalDataProvider provider : providers) {
            if (!provider.isReady()) {
                return true;
            }
        }
        return false;
    }

    private boolean isInstalledProviderPending(String pluginId) {
        String installedName = BUILT_IN_PROVIDERS.stream()
                .map(ProviderDefinition::pluginId)
                .filter(name -> name.equalsIgnoreCase(pluginId))
                .findFirst()
                .orElse(pluginId);
        Plugin plugin = Bukkit.getPluginManager().getPlugin(installedName);
        if (plugin == null) {
            return false;
        }
        Optional<ExternalDataProvider> provider = findProvider(pluginId);
        return !plugin.isEnabled() || provider.isEmpty() || !provider.get().isReady();
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
        Plugin plugin = registeredProvider.getPlugin();
        if (plugin != null && plugin.isEnabled()) {
            activateProvider(registeredProvider);
        }
    }

    public BlockData captureBlockData(BlockData blockData) {
        Objects.requireNonNull(blockData);
        if (blockData instanceof IrisCustomData) {
            return blockData;
        }
        for (ExternalDataProvider provider : activeProviders) {
            Optional<Identifier> identifier = provider.identifyBlock(blockData);
            if (identifier.isPresent()) {
                return IrisCustomData.of(blockData, qualifyBlockId(provider, identifier.get()));
            }
        }
        return blockData;
    }

    public Optional<BlockData> getBlockData(final Identifier key) {
        Pair<Identifier, KMap<String, String>> pair;
        try {
            pair = parseState(key);
        } catch (IllegalArgumentException e) {
            IrisLogging.error(e.getMessage());
            return Optional.empty();
        }
        try {
            BlockProvider match = findBlockProvider(pair.getA());
            if (match == null) {
                return Optional.empty();
            }
            BlockData resolved = match.provider().getBlockData(match.identifier(), pair.getB());
            if (resolved instanceof IrisCustomData custom) {
                resolved = IrisCustomData.of(custom.getBase(), qualifyBlockId(match.provider(), custom.getCustom()));
            }
            return Optional.of(resolved);
        } catch (MissingResourceException e) {
            IrisLogging.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return Optional.empty();
        } catch (RuntimeException | LinkageError e) {
            IrisLogging.reportError("Failed to resolve external block " + key + ".", e);
            return Optional.empty();
        }
    }

    public Optional<List<BlockProperty>> getBlockProperties(final Identifier key) {
        try {
            BlockProvider match = findBlockProvider(parseState(key).getA());
            return match == null ? Optional.empty() : Optional.of(match.provider().getBlockProperties(match.identifier()));
        } catch (MissingResourceException e) {
            IrisLogging.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return Optional.empty();
        } catch (RuntimeException | LinkageError e) {
            IrisLogging.reportError("Failed to resolve external block properties for " + key + ".", e);
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

    public void processUpdate(Engine engine, Block block, Identifier blockId) {
        Pair<Identifier, KMap<String, String>> state = parseState(blockId);
        BlockProvider match = findBlockProvider(state.getA());
        if (match == null) {
            throw new MissingResourceException("No matching provider found for external block placement.", blockId.namespace(), blockId.key());
        }
        match.provider().processUpdate(engine, block, buildState(match.identifier(), state.getB()));
    }

    public boolean placeBlock(Block block, Identifier blockId) {
        Pair<Identifier, KMap<String, String>> state = parseState(blockId);
        BlockProvider match = findBlockProvider(state.getA());
        if (match == null) {
            throw new MissingResourceException("No matching provider found for external block placement.", blockId.namespace(), blockId.key());
        }
        return match.provider().placeBlock(block, buildState(match.identifier(), state.getB()));
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
        if (dataType == DataType.BLOCK) {
            Set<Identifier> identifiers = new LinkedHashSet<>();
            for (ExternalDataProvider provider : activeProviders) {
                for (Identifier identifier : providerBlockTypes(provider)) {
                    identifiers.add(identifier);
                    identifiers.add(qualifyBlockId(provider, identifier));
                }
            }
            return List.copyOf(identifiers);
        }
        return activeProviders.stream()
                .flatMap(p -> p.getTypes(dataType).stream())
                .toList();
    }

    private Collection<Identifier> providerBlockTypes(ExternalDataProvider provider) {
        try {
            return provider.getTypes(DataType.BLOCK);
        } catch (RuntimeException | LinkageError error) {
            IrisLogging.reportError("Failed to enumerate blocks from " + provider.getPluginId() + ".", error);
            return List.of();
        }
    }

    public Collection<Pair<Identifier, List<BlockProperty>>> getAllBlockProperties() {
        List<Pair<Identifier, List<BlockProperty>>> properties = new ArrayList<>();
        for (Identifier identifier : getAllIdentifiers(DataType.BLOCK)) {
            getBlockProperties(identifier).ifPresent(values -> properties.add(new Pair<>(identifier, values)));
        }
        return properties;
    }

    public static Pair<Identifier, KMap<String, String>> parseState(Identifier key) {
        String raw = key.key();
        int open = raw.indexOf('[');
        int close = raw.lastIndexOf(']');
        if (open < 0 && close < 0) {
            return new Pair<>(key, new KMap<>());
        }
        if (open <= 0 || close != raw.length() - 1 || close < open
                || raw.indexOf('[', open + 1) >= 0 || raw.indexOf(']') != close) {
            throw new IllegalArgumentException("Malformed block state \"" + key + "\" (expected block[key=value])");
        }

        String state = raw.substring(open + 1, close);
        KMap<String, String> stateMap = new KMap<>();
        if (!state.isEmpty()) {
            for (String entry : state.split(",", -1)) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                    throw new IllegalArgumentException("Malformed block state \"" + entry + "\" in \"" + key + "\" (expected key=value)");
                }
                if (stateMap.put(pair[0].trim(), pair[1].trim()) != null) {
                    throw new IllegalArgumentException("Duplicate block property \"" + pair[0] + "\" in \"" + key + "\"");
                }
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

    private BlockProvider findBlockProvider(Identifier identifier) {
        int separator = identifier.key().indexOf('/');
        if (separator > 0) {
            for (ExternalDataProvider provider : activeProviders) {
                if (!provider.getPluginId().equalsIgnoreCase(identifier.namespace())) {
                    continue;
                }
                Identifier nativeId = new Identifier(identifier.key().substring(0, separator), identifier.key().substring(separator + 1));
                if (provider.isValidProvider(nativeId, DataType.BLOCK)) {
                    return new BlockProvider(provider, nativeId);
                }
                return provider.isValidProvider(identifier, DataType.BLOCK) ? new BlockProvider(provider, identifier) : null;
            }
            if (BUILT_IN_PROVIDERS.stream().anyMatch(definition -> definition.pluginId().equalsIgnoreCase(identifier.namespace()))
                    || findProvider(identifier.namespace()).isPresent()) {
                return null;
            }
        }

        BlockProvider match = null;
        for (ExternalDataProvider provider : activeProviders) {
            if (!provider.isValidProvider(identifier, DataType.BLOCK)) {
                continue;
            }
            if (match != null) {
                IrisLogging.warnOnce("external-provider:ambiguous-block:" + identifier,
                        "Multiple providers own block %s. Use %s or %s to select one.", identifier,
                        qualifyBlockId(match.provider(), identifier), qualifyBlockId(provider, identifier));
                return null;
            }
            match = new BlockProvider(provider, identifier);
        }
        return match;
    }

    private static Identifier qualifyBlockId(ExternalDataProvider provider, Identifier identifier) {
        return new Identifier(provider.getPluginId().toLowerCase(Locale.ROOT), identifier.namespace() + "/" + identifier.key());
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
            notifyContentChanged();
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

    private record BlockProvider(ExternalDataProvider provider, Identifier identifier) {
    }
}
