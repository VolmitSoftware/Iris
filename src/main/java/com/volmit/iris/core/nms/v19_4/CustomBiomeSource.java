package com.volmit.iris.core.nms.v19_4;

import com.mojang.serialization.Codec;
import com.volmit.iris.Iris;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_19_R3.CraftServer;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.block.CraftBlock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return getAllBiomes(
                ((RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()))
                        .registry(Registries.BIOME).orElse(null),
                ((CraftWorld) engine.getWorld().realWorld()).getHandle().registryAccess().registry(Registries.BIOME).orElse(null),
                engine).stream();
    }

    private static List<Holder<Biome>> getAllBiomes(Registry<Biome> customRegistry, Registry<Biome> registry, Engine engine) {
        List<Holder<Biome>> b = new ArrayList<>();

        for (IrisBiome i : engine.getAllBiomes()) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    b.add(customRegistry.getHolder(customRegistry.getResourceKey(customRegistry
                            .get(new ResourceLocation(engine.getDimension().getLoadKey() + ":" + j.getId()))).get()).get());
                }
            } else {
                b.add(CraftBlock.biomeToBiomeBase(registry, i.getVanillaDerivative()));
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

    private KMap<String, Holder<Biome>> fillCustomBiomes(Registry<Biome> customRegistry, Engine engine) {
        KMap<String, Holder<Biome>> m = new KMap<>();

        for (IrisBiome i : engine.getAllBiomes()) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    m.put(j.getId(), customRegistry.getHolder(customRegistry.getResourceKey(customRegistry
                            .get(new ResourceLocation(engine.getDimension().getLoadKey() + ":" + j.getId()))).get()).get());
                }
            }
        }

        return m;
    }

    private RegistryAccess registry() {
        return registryAccess.aquire(() -> (RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer()));
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
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
            return CraftBlock.biomeToBiomeBase(biomeRegistry, v);
        }
    }
}