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

package com.volmit.iris.core.nms.v1_21_R1;

import com.mojang.serialization.MapCodec;
import com.volmit.iris.Iris;
import com.volmit.iris.core.ServerConfigurator;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.RNG;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.craftbukkit.v1_21_R1.CraftWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class CustomBiomeSource extends BiomeSource {

    private final long seed;
    private final Engine engine;
    private final Registry<Biome> biomeCustomRegistry;
    private final Registry<Biome> biomeRegistry;
    private final AtomicCache<RegistryAccess> registryAccess = new AtomicCache<>();
    private final RNG rng;
    private final KMap<String, Holder<Biome>> customBiomes;

    public CustomBiomeSource(long seed, Engine engine, World world) {
        this.engine = engine;
        this.seed = seed;
        this.biomeCustomRegistry = registry().registry(Registries.BIOME).orElse(null);
        this.biomeRegistry = ((RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer())).registry(Registries.BIOME).orElse(null);
        this.rng = new RNG(engine.getSeedManager().getBiome());
        this.customBiomes = fillCustomBiomes(biomeCustomRegistry, engine);
    }

    private static List<Holder<Biome>> getAllBiomes(Registry<Biome> customRegistry, Registry<Biome> registry, Engine engine) {
        List<Holder<Biome>> b = new ArrayList<>();

        for (IrisBiome i : engine.getAllBiomes()) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    b.add(customRegistry.getHolder(customRegistry.getResourceKey(customRegistry
                            .get(ResourceLocation.fromNamespaceAndPath(engine.getDimension().getLoadKey(), j.getId()))).get()).get());
                }
            } else {
                b.add(NMSBinding.biomeToBiomeBase(registry, i.getVanillaDerivative()));
            }
        }

        return b;
    }

    private static Object getFor(Class<?> type, Object source) {
        Object o = fieldFor(type, source);

        if (o != null) {
            return o;
        }

        return invokeFor(type, source);
    }

    private static Object fieldFor(Class<?> returns, Object in) {
        return fieldForClass(returns, in.getClass(), in);
    }

    private static Object invokeFor(Class<?> returns, Object in) {
        for (Method i : in.getClass().getMethods()) {
            if (i.getReturnType().equals(returns)) {
                i.setAccessible(true);
                try {
                    Iris.debug("[NMS] Found " + returns.getSimpleName() + " in " + in.getClass().getSimpleName() + "." + i.getName() + "()");
                    return i.invoke(in);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldForClass(Class<T> returnType, Class<?> sourceType, Object in) {
        for (Field i : sourceType.getDeclaredFields()) {
            if (i.getType().equals(returnType)) {
                i.setAccessible(true);
                try {
                    Iris.debug("[NMS] Found " + returnType.getSimpleName() + " in " + sourceType.getSimpleName() + "." + i.getName());
                    return (T) i.get(in);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return getAllBiomes(
                ((RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()))
                        .registry(Registries.BIOME).orElse(null),
                ((CraftWorld) engine.getWorld().realWorld()).getHandle().registryAccess().registry(Registries.BIOME).orElse(null),
                engine).stream();
    }
    private KMap<String, Holder<Biome>> fillCustomBiomes(Registry<Biome> customRegistry, Engine engine) {
        KMap<String, Holder<Biome>> m = new KMap<>();

        for (IrisBiome i : engine.getAllBiomes()) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    ResourceLocation location = ResourceLocation.fromNamespaceAndPath(engine.getDimension().getLoadKey(), j.getId());
                    Biome biome = customRegistry.get(location);
                    if (biome == null) {
                        INMS.get().registerBiome(location.getNamespace(), j, false);
                        biome = customRegistry.get(location);
                        if (biome == null) {
                            Iris.error("Cannot find biome for IrisBiomeCustom " + j.getId() + " from engine " + engine.getName());
                            continue;
                        }
                    }
                    Optional<ResourceKey<Biome>> optionalBiomeKey = customRegistry.getResourceKey(biome);
                    if (optionalBiomeKey.isEmpty()) {
                        Iris.error("Cannot find biome for IrisBiomeCustom " + j.getId() + " from engine " + engine.getName());
                        continue;
                    }
                    ResourceKey<Biome> biomeKey = optionalBiomeKey.get();
                    Optional<Holder.Reference<Biome>> optionalReferenceHolder = customRegistry.getHolder(biomeKey);
                    if (optionalReferenceHolder.isEmpty()) {
                        Iris.error("Cannot find reference to biome " + biomeKey + " for engine " + engine.getName());
                        continue;
                    }
                    m.put(j.getId(), optionalReferenceHolder.get());
                }
            }
        }
        ServerConfigurator.dumpDataPack();

        return m;
    }

    private RegistryAccess registry() {
        return registryAccess.aquire(() -> (RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int m = (y - engine.getMinHeight()) << 2;
        IrisBiome ib = engine.getComplex().getTrueBiomeStream().get(x << 2, z << 2);
        if (ib.isCustom()) {
            return customBiomes.get(ib.getCustomBiome(rng, x << 2, m, z << 2).getId());
        } else {
            org.bukkit.block.Biome v = ib.getSkyBiome(rng, x << 2, m, z << 2);
            return NMSBinding.biomeToBiomeBase(biomeRegistry, v);
        }
    }
}