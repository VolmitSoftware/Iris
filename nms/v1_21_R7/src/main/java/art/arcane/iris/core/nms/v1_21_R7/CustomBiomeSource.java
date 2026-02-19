package art.arcane.iris.core.nms.v1_21_R7;

import com.mojang.serialization.MapCodec;
import art.arcane.iris.Iris;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_21_R7.CraftServer;
import org.bukkit.craftbukkit.v1_21_R7.CraftWorld;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private final Holder<Biome> fallbackBiome;

    public CustomBiomeSource(long seed, Engine engine, World world) {
        this.engine = engine;
        this.seed = seed;
        this.biomeCustomRegistry = registry().lookup(Registries.BIOME).orElse(null);
        this.biomeRegistry = ((RegistryAccess) getFor(RegistryAccess.Frozen.class, ((CraftServer) Bukkit.getServer()).getHandle().getServer())).lookup(Registries.BIOME).orElse(null);
        this.rng = new RNG(engine.getSeedManager().getBiome());
        this.fallbackBiome = resolveFallbackBiome(this.biomeRegistry, this.biomeCustomRegistry);
        this.customBiomes = fillCustomBiomes(this.biomeCustomRegistry, engine, this.fallbackBiome);
    }

    private static List<Holder<Biome>> getAllBiomes(Registry<Biome> customRegistry, Registry<Biome> registry, Engine engine, Holder<Biome> fallback) {
        LinkedHashSet<Holder<Biome>> biomes = new LinkedHashSet<>();
        if (fallback != null) {
            biomes.add(fallback);
        }

        for (IrisBiome i : engine.getAllBiomes()) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    Holder<Biome> customHolder = resolveCustomBiomeHolder(customRegistry, engine, j.getId());
                    if (customHolder != null) {
                        biomes.add(customHolder);
                    } else if (fallback != null) {
                        biomes.add(fallback);
                    }
                }
            } else {
                Holder<Biome> vanillaHolder = NMSBinding.biomeToBiomeBase(registry, i.getVanillaDerivative());
                if (vanillaHolder != null) {
                    biomes.add(vanillaHolder);
                } else if (fallback != null) {
                    biomes.add(fallback);
                }
            }
        }

        return new ArrayList<>(biomes);
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
                        .lookup(Registries.BIOME).orElse(null),
                ((CraftWorld) engine.getWorld().realWorld()).getHandle().registryAccess().lookup(Registries.BIOME).orElse(null),
                engine,
                fallbackBiome).stream();
    }

    private KMap<String, Holder<Biome>> fillCustomBiomes(Registry<Biome> customRegistry, Engine engine, Holder<Biome> fallback) {
        KMap<String, Holder<Biome>> m = new KMap<>();
        if (customRegistry == null) {
            return m;
        }

        for (IrisBiome i : engine.getAllBiomes()) {
            if (i.isCustom()) {
                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    Holder<Biome> holder = resolveCustomBiomeHolder(customRegistry, engine, j.getId());
                    if (holder == null) {
                        if (fallback != null) {
                            m.put(j.getId(), fallback);
                        }
                        Iris.error("Cannot find biome for IrisBiomeCustom " + j.getId() + " from engine " + engine.getName());
                        continue;
                    }
                    m.put(j.getId(), holder);
                }
            }
        }

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
        if (ib == null) {
            return resolveFallbackBiome(biomeRegistry, biomeCustomRegistry);
        }

        if (ib.isCustom()) {
            IrisBiomeCustom custom = ib.getCustomBiome(rng, x << 2, m, z << 2);
            if (custom != null) {
                Holder<Biome> holder = customBiomes.get(custom.getId());
                if (holder != null) {
                    return holder;
                }
            }

            return resolveFallbackBiome(biomeRegistry, biomeCustomRegistry);
        }

        org.bukkit.block.Biome v = ib.getSkyBiome(rng, x << 2, m, z << 2);
        Holder<Biome> holder = NMSBinding.biomeToBiomeBase(biomeRegistry, v);
        if (holder != null) {
            return holder;
        }

        return resolveFallbackBiome(biomeRegistry, biomeCustomRegistry);
    }

    private static Holder<Biome> resolveCustomBiomeHolder(Registry<Biome> customRegistry, Engine engine, String customBiomeId) {
        if (customRegistry == null || engine == null || customBiomeId == null || customBiomeId.isBlank()) {
            return null;
        }

        Identifier resourceLocation = Identifier.fromNamespaceAndPath(
                engine.getDimension().getLoadKey().toLowerCase(java.util.Locale.ROOT),
                customBiomeId.toLowerCase(java.util.Locale.ROOT)
        );
        Biome biome = customRegistry.getValue(resourceLocation);
        if (biome == null) {
            return null;
        }

        Optional<ResourceKey<Biome>> optionalBiomeKey = customRegistry.getResourceKey(biome);
        if (optionalBiomeKey.isEmpty()) {
            return null;
        }

        Optional<Holder.Reference<Biome>> optionalReferenceHolder = customRegistry.get(optionalBiomeKey.get());
        if (optionalReferenceHolder.isEmpty()) {
            return null;
        }

        return optionalReferenceHolder.get();
    }

    private static Holder<Biome> resolveFallbackBiome(Registry<Biome> registry, Registry<Biome> customRegistry) {
        Holder<Biome> plains = NMSBinding.biomeToBiomeBase(registry, org.bukkit.block.Biome.PLAINS);
        if (plains != null) {
            return plains;
        }

        Holder<Biome> vanilla = firstHolder(registry);
        if (vanilla != null) {
            return vanilla;
        }

        return firstHolder(customRegistry);
    }

    private static Holder<Biome> firstHolder(Registry<Biome> registry) {
        if (registry == null) {
            return null;
        }

        for (Biome biome : registry) {
            Optional<ResourceKey<Biome>> optionalBiomeKey = registry.getResourceKey(biome);
            if (optionalBiomeKey.isEmpty()) {
                continue;
            }

            Optional<Holder.Reference<Biome>> optionalHolder = registry.get(optionalBiomeKey.get());
            if (optionalHolder.isPresent()) {
                return optionalHolder.get();
            }
        }

        return null;
    }
}
