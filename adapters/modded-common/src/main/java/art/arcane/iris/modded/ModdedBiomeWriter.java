/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modded;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionTerrainContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.generation.context.IrisContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class ModdedBiomeWriter implements PlatformBiomeWriter {
    private static final String VANILLA_FALLBACK_KEY = "minecraft:plains";
    private static final int MAX_CACHED_IDS = 4096;
    /** NUL cannot occur in a pack or registry key, so the composite cache key stays unambiguous. */
    private static final char SCOPE_SEPARATOR = (char) 0;

    private final Supplier<MinecraftServer> server;
    private final AtomicBoolean serverMissingReported = new AtomicBoolean();
    private volatile RegistryCache cache;

    public ModdedBiomeWriter(Supplier<MinecraftServer> server) {
        this.server = server;
    }

    @Override
    public int biomeIdFor(String key) {
        Registry<Biome> registry = biomeRegistry();
        if (registry == null) {
            reportMissingServer("resolve the biome id for '" + key + "'", "using biome id 0");
            return 0;
        }
        if (key == null) {
            ModdedIrisLog.warn("Iris biome writer got a null biome key; falling back to {}", VANILLA_FALLBACK_KEY);
            return fallbackId(registry);
        }

        RegistryCache cached = cacheFor(registry);
        String scoped = scopedBiomeKey(key);
        // The scoped key depends on the calling engine, so the same pack key can resolve differently per
        // dimension. Cache on both halves; the derivative path below reads the raw key.
        String cacheKey = scoped.equals(key) ? key : key + SCOPE_SEPARATOR + scoped;
        Integer hit = cached.ids.get(cacheKey);
        if (hit != null) {
            return hit;
        }

        int resolved = resolve(registry, key, scoped);
        if (cached.ids.size() < MAX_CACHED_IDS) {
            cached.ids.put(cacheKey, resolved);
        }
        return resolved;
    }

    private int resolve(Registry<Biome> registry, String key, String scoped) {
        int direct = idForKey(registry, scoped);
        if (direct >= 0) {
            return direct;
        }
        int derivative = idForDerivative(registry, key);
        if (derivative >= 0) {
            return derivative;
        }
        return fallbackId(registry);
    }

    private String scopedBiomeKey(String key) {
        IrisContext context = IrisContext.get();
        if (context == null || key == null) {
            return key;
        }
        Engine engine = context.getEngine();
        BiomeOwner owner = findCustomBiomeOwner(engine, key);
        if (owner != null) {
            return owner.physicalKey();
        }
        return engine.getData().physicalBiomeResourceKey(engine.getDimension(), key);
    }

    @Override
    public List<PlatformBiome> allBiomes() {
        Registry<Biome> registry = biomeRegistry();
        if (registry == null) {
            reportMissingServer("enumerate the biome registry", "returning no biomes");
            return new ArrayList<>();
        }

        RegistryCache cached = cacheFor(registry);
        List<PlatformBiome> snapshot = cached.biomes;
        if (snapshot == null) {
            List<PlatformBiome> built = new ArrayList<>();
            for (Identifier identifier : registry.keySet()) {
                Biome biome = registry.getValue(identifier);
                if (biome != null) {
                    built.add(ModdedBiome.of(biome, identifier.toString()));
                }
            }
            snapshot = List.copyOf(built);
            cached.biomes = snapshot;
        }
        return new ArrayList<>(snapshot);
    }

    private int idForKey(Registry<Biome> registry, String key) {
        Identifier identifier = Identifier.tryParse(key);
        if (identifier == null) {
            return -1;
        }
        Biome biome = registry.getValue(identifier);
        return biome == null ? -1 : registry.getId(biome);
    }

    private int idForDerivative(Registry<Biome> registry, String key) {
        BiomeOwner owner = findCustomBiomeOwner(key);
        if (owner == null) {
            return -1;
        }
        String derivativeKey = owner.biome().getVanillaDerivativeKey();
        if (derivativeKey == null) {
            return -1;
        }
        return idForKey(registry, derivativeKey);
    }

    private BiomeOwner findCustomBiomeOwner(String key) {
        for (Engine engine : ModdedWorldEngines.activeEngines()) {
            if (engine == null || engine.isClosed()) {
                continue;
            }
            BiomeOwner owner = findCustomBiomeOwner(engine, key);
            if (owner != null) {
                return owner;
            }
        }
        return null;
    }

    private BiomeOwner findCustomBiomeOwner(Engine engine, String key) {
        IrisDimension hostDimension = engine.getDimension();
        BiomeOwner owner = findCustomBiomeOwner(
                hostDimension,
                engine.getData(),
                hostDimension.getAllBiomes(engine),
                key
        );
        if (owner != null) {
            return owner;
        }
        DimensionStackContext stackContext = engine.getDimensionStackContext();
        if (stackContext == null) {
            return null;
        }
        for (DimensionTerrainContext terrainContext : stackContext.getLayersBottomToTop()) {
            if (terrainContext.isSelfReferencing()) {
                continue;
            }
            IrisDimension dimension = terrainContext.getDimension();
            owner = findCustomBiomeOwner(
                    dimension,
                    terrainContext.getData(),
                    dimension.getAllBiomes(terrainContext),
                    key
            );
            if (owner != null) {
                return owner;
            }
        }
        return null;
    }

    private BiomeOwner findCustomBiomeOwner(
            IrisDimension dimension,
            IrisData data,
            Iterable<IrisBiome> biomes,
            String key
    ) {
        for (IrisBiome biome : biomes) {
            if (!biome.isCustom()) {
                continue;
            }
            for (IrisBiomeCustom custom : biome.getCustomDerivitives()) {
                String contractLogicalKey = dimension.getLoadKey().toLowerCase(Locale.ROOT)
                        + ":" + custom.getId().toLowerCase(Locale.ROOT);
                String physicalKey = data.customBiomeResourceKey(dimension, custom);
                if (key.equalsIgnoreCase(dimension.getCustomBiomeKey(custom.getId()))
                        || key.equalsIgnoreCase(contractLogicalKey)
                        || key.equalsIgnoreCase(physicalKey)) {
                    return new BiomeOwner(dimension, data, biome, physicalKey);
                }
            }
        }
        return null;
    }

    private int fallbackId(Registry<Biome> registry) {
        int plains = idForKey(registry, VANILLA_FALLBACK_KEY);
        return plains >= 0 ? plains : 0;
    }

    private Registry<Biome> biomeRegistry() {
        MinecraftServer instance = server.get();
        if (instance == null) {
            return null;
        }
        // Read before write: this runs for every biome id on every generation thread, and an unconditional
        // store on a shared cache line is a contended write on the hot path for a flag that is almost always
        // already false.
        if (serverMissingReported.get()) {
            serverMissingReported.set(false);
        }
        return instance.registryAccess().lookupOrThrow(Registries.BIOME);
    }

    /**
     * The SPI requires biome writers to cache their registry lookups: biomeIdFor runs from generation threads
     * for every biome a pack names, and the derivative path walks every active engine and every custom biome.
     * The cache is keyed on the biome Registry instance, which the server replaces whenever datapacks reload,
     * so a reload invalidates everything for free.
     */
    private RegistryCache cacheFor(Registry<Biome> registry) {
        RegistryCache current = cache;
        if (current != null && current.registry == registry) {
            return current;
        }
        RegistryCache replacement = new RegistryCache(registry);
        cache = replacement;
        return replacement;
    }

    private void reportMissingServer(String operation, String fallback) {
        if (serverMissingReported.compareAndSet(false, true)) {
            ModdedIrisLog.warn("Iris cannot {} before the Minecraft server is available; {}", operation, fallback);
        }
    }

    private static final class RegistryCache {
        private final Registry<Biome> registry;
        private final ConcurrentHashMap<String, Integer> ids = new ConcurrentHashMap<>();
        private volatile List<PlatformBiome> biomes;

        private RegistryCache(Registry<Biome> registry) {
            this.registry = registry;
        }
    }

    private record BiomeOwner(IrisDimension dimension, IrisData data, IrisBiome biome, String physicalKey) {
    }
}
