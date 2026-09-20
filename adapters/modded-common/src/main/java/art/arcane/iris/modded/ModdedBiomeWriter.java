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
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBiomeRegistry;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.generation.context.IrisContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModdedBiomeWriter implements PlatformBiomeWriter {
    private static final String VANILLA_FALLBACK_KEY = "minecraft:plains";
    private final NativeBiomeRegistry biomes;
    private final AtomicBoolean serverMissingReported = new AtomicBoolean();

    public ModdedBiomeWriter(NativeBiomeRegistry biomes) {
        this.biomes = biomes;
    }

    @Override
    public int biomeIdFor(String key) {
        NativeBiomeRegistry.View registry = biomeRegistry();
        if (registry == null) {
            reportMissingServer("resolve the biome id for '" + key + "'", "using biome id 0");
            return 0;
        }
        if (key == null) {
            ModdedIrisLog.warn("Iris biome writer got a null biome key; falling back to {}", VANILLA_FALLBACK_KEY);
            return registry.fallbackId();
        }

        return registry.resolve(key, scopedBiomeKey(key), this::derivativeKey);
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
    public List<NativeBiome> allBiomes() {
        NativeBiomeRegistry.View registry = biomeRegistry();
        if (registry == null) {
            reportMissingServer("enumerate the biome registry", "returning no biomes");
            return new ArrayList<>();
        }

        return registry.allBiomes();
    }

    private String derivativeKey(String key) {
        BiomeOwner owner = findCustomBiomeOwner(key);
        return owner == null ? null : owner.biome().getVanillaDerivativeKey();
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

    private NativeBiomeRegistry.View biomeRegistry() {
        NativeBiomeRegistry.View registry = biomes.current();
        if (registry != null && serverMissingReported.get()) {
            serverMissingReported.set(false);
        }
        return registry;
    }

    private void reportMissingServer(String operation, String fallback) {
        if (serverMissingReported.compareAndSet(false, true)) {
            ModdedIrisLog.warn("Iris cannot {} before the Minecraft server is available; {}", operation, fallback);
        }
    }

    private record BiomeOwner(IrisDimension dimension, IrisData data, IrisBiome biome, String physicalKey) {
    }
}
