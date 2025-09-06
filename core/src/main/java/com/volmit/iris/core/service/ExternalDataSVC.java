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

package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.core.link.*;
import com.volmit.iris.core.link.data.DataType;
import com.volmit.iris.core.nms.container.BlockProperty;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.io.JarScanner;
import com.volmit.iris.util.plugin.IrisService;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class ExternalDataSVC implements IrisService {

    private KList<ExternalDataProvider> providers = new KList<>(), activeProviders = new KList<>();

    @Override
    public void onEnable() {
        Iris.info("Loading ExternalDataProvider...");
        Bukkit.getPluginManager().registerEvents(this, Iris.instance);

        providers.addAll(createProviders());
        for (ExternalDataProvider p : providers) {
            if (p.isReady()) {
                activeProviders.add(p);
                p.init();
                Iris.instance.registerListener(p);
                Iris.info("Enabled ExternalDataProvider for %s.", p.getPluginId());
            }
        }
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        if (activeProviders.stream().noneMatch(p -> e.getPlugin().equals(p.getPlugin()))) {
            providers.stream().filter(p -> p.isReady() && e.getPlugin().equals(p.getPlugin())).findFirst().ifPresent(edp -> {
                activeProviders.add(edp);
                edp.init();
                Iris.instance.registerListener(edp);
                Iris.info("Enabled ExternalDataProvider for %s.", edp.getPluginId());
            });
        }
    }

    public void registerProvider(@NonNull ExternalDataProvider provider) {
        String plugin = provider.getPluginId();
        if (providers.stream().map(ExternalDataProvider::getPluginId).anyMatch(plugin::equals))
            throw new IllegalArgumentException("A provider with the same plugin id already exists.");

        providers.add(provider);
        if (provider.isReady()) {
            activeProviders.add(provider);
            provider.init();
            Iris.instance.registerListener(provider);
        }
    }

    public Optional<BlockData> getBlockData(final Identifier key) {
        var pair = parseState(key);
        Identifier mod = pair.getA();

        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(mod, DataType.BLOCK)).findFirst();
        if (provider.isEmpty())
            return Optional.empty();
        try {
            return Optional.of(provider.get().getBlockData(mod, pair.getB()));
        } catch (MissingResourceException e) {
            Iris.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return Optional.empty();
        }
    }

    public Optional<List<BlockProperty>> getBlockProperties(final Identifier key) {
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(key, DataType.BLOCK)).findFirst();
        if (provider.isEmpty())
            return Optional.empty();
        try {
            return Optional.of(provider.get().getBlockProperties(key));
        } catch (MissingResourceException e) {
            Iris.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return Optional.empty();
        }
    }

    public Optional<ItemStack> getItemStack(Identifier key, KMap<String, Object> customNbt) {
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(key, DataType.ITEM)).findFirst();
        if (provider.isEmpty()) {
            Iris.warn("No matching Provider found for modded material \"%s\"!", key);
            return Optional.empty();
        }
        try {
            return Optional.of(provider.get().getItemStack(key, customNbt));
        } catch (MissingResourceException e) {
            Iris.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return Optional.empty();
        }
    }

    public void processUpdate(Engine engine, Block block, Identifier blockId) {
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(blockId, DataType.BLOCK)).findFirst();
        if (provider.isEmpty()) {
            Iris.warn("No matching Provider found for modded material \"%s\"!", blockId);
            return;
        }
        provider.get().processUpdate(engine, block, blockId);
    }

    public Entity spawnMob(Location location, Identifier mobId) {
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(mobId, DataType.ENTITY)).findFirst();
        if (provider.isEmpty()) {
            Iris.warn("No matching Provider found for modded mob \"%s\"!", mobId);
            return null;
        }
        try {
            return provider.get().spawnMob(location, mobId);
        } catch (MissingResourceException e) {
            Iris.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
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

    public static Pair<Identifier, KMap<String, String>> parseState(Identifier key) {
        if (!key.key().contains("[") || !key.key().contains("]")) {
            return new Pair<>(key, new KMap<>());
        }
        String state = key.key().split("\\Q[\\E")[1].split("\\Q]\\E")[0];
        KMap<String, String> stateMap = new KMap<>();
        if (!state.isEmpty()) {
            Arrays.stream(state.split(",")).forEach(s -> stateMap.put(s.split("=")[0], s.split("=")[1]));
        }
        return new Pair<>(new Identifier(key.namespace(), key.key().split("\\Q[\\E")[0]), stateMap);
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

    private static KList<ExternalDataProvider> createProviders() {
        JarScanner jar = new JarScanner(Iris.instance.getJarFile(), "com.volmit.iris.core.link.data", false);
        J.attempt(jar::scan);
        KList<ExternalDataProvider> providers = new KList<>();

        for (Class<?> c : jar.getClasses()) {
            if (ExternalDataProvider.class.isAssignableFrom(c)) {
                try {
                    ExternalDataProvider p = (ExternalDataProvider) c.getDeclaredConstructor().newInstance();
                    if (p.getPlugin() != null) Iris.info(p.getPluginId() + " found, loading " + c.getSimpleName() + "...");
                    providers.add(p);
                } catch (Throwable ignored) {}
            }
        }
        return providers;
    }
}
