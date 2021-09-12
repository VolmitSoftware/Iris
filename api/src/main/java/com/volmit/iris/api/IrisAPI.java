/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.api;

import com.volmit.iris.Iris;
import com.volmit.iris.core.service.RegistrySVC;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.util.api.APIAwareBlock;
import com.volmit.iris.util.api.APIWorldBlock;
import com.volmit.iris.util.plugin.PluginRegistryGroup;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.function.Supplier;

public class IrisAPI
{
    private static final AtomicCache<RegistryHolder<Supplier<BlockData>>> customBlock = new AtomicCache<>();

    /**
     * Checks if the given world is an Iris World
     *
     * @param world the world
     * @return true if it is an Iris world
     */
    public static boolean isIrisWorld(World world) {
        return IrisToolbelt.isIrisWorld(world);
    }

    /**
     * Checks if the given world is an Iris World with studio mode
     * @param world the world
     * @return true if it is an Iris World & is in Studio Mode
     */
    public static boolean isIrisStudioWorld(World world) {
        return IrisToolbelt.isIrisStudioWorld(world);
    }

    /**
     * Used for registering ids into custom blocks
     * @return the registry
     */
    public static RegistryHolder<Supplier<BlockData>> getCustomBlockRegistry()
    {
        return customBlock.aquire(() -> new RegistryHolder<>(() -> Iris.service(RegistrySVC.class).getCustomBlockRegistry()));
    }

    public static class RegistryHolder<T>
    {
        private final Supplier<PluginRegistryGroup<T>> registry;

        public RegistryHolder(Supplier<PluginRegistryGroup<T>> registry)
        {
            this.registry = registry;
        }

        /**
         * Unregister a node
         * @param namespace the namespace (your plugin id or something)
         * @param id the identifier for the node
         */
        public void unregister(String namespace, String id)
        {
            registry.get().getRegistry(namespace).unregister(id);
        }

        /**
         * Unregister all nodes by namespace
         * @param namespace the namespace (such as your plugin)
         */
        public void unregisterAll(String namespace)
        {
            registry.get().getRegistry(namespace).unregisterAll();
        }

        /**
         * Register a node under a namespace & id
         * such as myplugin:ruby_ore which should resolve to an object that
         * could be used by the generator.
         *
         * @param namespace the namespace (of this plugin) (myplugin)
         * @param id the identifier for this node (ruby_ore)
         * @param block the provider for this node data (always return a new instance!)
         */
        public void register(String namespace, String id, T block)
        {
            registry.get().getRegistry(namespace).register(id, block);
        }

        /**
         * Get all registered nodes under a namespace
         * @param namespace the namespace such as your plugin
         * @return the registry list (ids)
         */
        public List<String> getRegsitries(String namespace)
        {
            return registry.get().getRegistry(namespace).getRegistries();
        }
    }

    @FunctionalInterface
    public interface AwareBlockMirror
    {
        void onPlaced(BlockData block, String namespace, String key, int x, int y, int z);

        default APIAwareBlock map()
        {
            return this::onPlaced;
        }
    }

    @FunctionalInterface
    public interface WorldBlockMirror
    {
        void onWorldPlaced(Block block, String namespace, String key);

        default APIWorldBlock map()
        {
            return this::onWorldPlaced;
        }
    }
}
