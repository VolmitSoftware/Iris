/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.plugin.IrisService;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
public class ExternalDataSVC implements IrisService {

    private KList<ExternalDataProvider> providers = new KList<>(), activeProviders = new KList<>();

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

    @Override
    public void onEnable() {
        Iris.info("Loading ExternalDataProvider...");
        Bukkit.getPluginManager().registerEvents(this, Iris.instance);

        providers.add(new OraxenDataProvider());
        if (Bukkit.getPluginManager().getPlugin("Oraxen") != null) {
            Iris.info("Oraxen found, loading OraxenDataProvider...");
        }
        providers.add(new ItemAdderDataProvider());
        if (Bukkit.getPluginManager().getPlugin("ItemAdder") != null) {
            Iris.info("ItemAdder found, loading ItemAdderDataProvider...");
        }
        providers.add(new ExecutableItemsDataProvider());
        if (Bukkit.getPluginManager().getPlugin("ExecutableItems") != null) {
            Iris.info("ExecutableItems found, loading ExecutableItemsDataProvider...");
        }
        providers.add(new HMCLeavesDataProvider());
        if (Bukkit.getPluginManager().getPlugin("HMCLeaves") != null) {
            Iris.info("BlockAdder found, loading HMCLeavesDataProvider...");
        }
        providers.add(new MMOItemsDataProvider());
        if (Bukkit.getPluginManager().getPlugin("MMOItems") != null) {
            Iris.info("MMOItems found, loading MMOItemsDataProvider...");
        }
        providers.add(new EcoItemsDataProvider());
        if (Bukkit.getPluginManager().getPlugin("EcoItems") != null) {
            Iris.info("EcoItems found, loading EcoItemsDataProvider...");
        }

        for (ExternalDataProvider p : providers) {
            if (p.isReady()) {
                activeProviders.add(p);
                p.init();
                Iris.info("Enabled ExternalDataProvider for %s.", p.getPluginId());
            }
        }
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        if (activeProviders.stream().noneMatch(p -> p.getPlugin().equals(e.getPlugin()))) {
            providers.stream().filter(p -> p.isReady() && p.getPlugin().equals(e.getPlugin())).findFirst().ifPresent(edp -> {
                activeProviders.add(edp);
                edp.init();
                Iris.info("Enabled ExternalDataProvider for %s.", edp.getPluginId());
            });
        }
    }

    public Optional<BlockData> getBlockData(final Identifier key) {
        var pair = parseState(key);
        Identifier mod = pair.getA();

        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(mod, false)).findFirst();
        if (provider.isEmpty())
            return Optional.empty();
        try {
            return Optional.of(provider.get().getBlockData(mod, pair.getB()));
        } catch (MissingResourceException e) {
            Iris.error(e.getMessage() + " - [" + e.getClassName() + ":" + e.getKey() + "]");
            return Optional.empty();
        }
    }

    public Optional<ItemStack> getItemStack(Identifier key, KMap<String, Object> customNbt) {
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(key, true)).findFirst();
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
        Optional<ExternalDataProvider> provider = activeProviders.stream().filter(p -> p.isValidProvider(blockId, false)).findFirst();
        if (provider.isEmpty()) {
            Iris.warn("No matching Provider found for modded material \"%s\"!", blockId);
            return;
        }
        provider.get().processUpdate(engine, block, blockId);
    }

    public Identifier[] getAllBlockIdentifiers() {
        KList<Identifier> names = new KList<>();
        activeProviders.forEach(p -> names.add(p.getBlockTypes()));
        return names.toArray(new Identifier[0]);
    }

    public Identifier[] getAllItemIdentifiers() {
        KList<Identifier> names = new KList<>();
        activeProviders.forEach(p -> names.add(p.getItemTypes()));
        return names.toArray(new Identifier[0]);
    }
}
