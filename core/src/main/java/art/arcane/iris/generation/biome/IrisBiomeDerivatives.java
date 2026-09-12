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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.bukkit.registry.RegistryUtil;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;

/**
 * Vanilla derivative and biome scatter resolution for {@link IrisBiome}. IrisBiome is Gson
 * deserialized from pack JSON, so its fields stay put and only the behavior lives here.
 */
final class IrisBiomeDerivatives {
    private IrisBiomeDerivatives() {
    }

    static String namespacedBiomeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        return trimmed.indexOf(':') >= 0 ? trimmed : "minecraft:" + trimmed;
    }

    static String getVanillaDerivativeKey(String derivative, String vanillaDerivative) {
        String resolved = namespacedBiomeKey(vanillaDerivative);
        return resolved == null ? namespacedBiomeKey(derivative) : resolved;
    }

    static String getStructureDerivativeKey(IrisBiome biome) {
        String key = biome.getVanillaDerivativeKey();
        if (key == null || !key.startsWith("minecraft:")) {
            return key;
        }
        if (biome.isSea() && !isVanillaSeaStructureBiome(key)) {
            return "minecraft:the_void";
        }
        if (biome.isShore() && !isVanillaShoreStructureBiome(key)) {
            return "minecraft:beach";
        }
        return key;
    }

    static boolean isVanillaSeaStructureBiome(String key) {
        return key != null && (key.contains("ocean") || key.endsWith("river"));
    }

    static boolean isVanillaShoreStructureBiome(String key) {
        return key != null && (key.endsWith("beach") || key.endsWith("shore"));
    }

    static KList<Biome> resolveBiomeKeys(KList<String> keys) {
        KList<Biome> resolved = new KList<>();
        for (String key : keys) {
            resolved.add(resolveBiomeKey(key));
        }
        return resolved;
    }

    static Biome resolveBiomeKey(String key) {
        if (key == null) {
            return null;
        }
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        return namespacedKey == null ? null : RegistryUtil.lookup(Biome.class).get(namespacedKey);
    }

    static Biome getSkyBiome(IrisBiome biome, RNG rng, Engine engine, double x, double y, double z) {
        KList<String> biomeSkyScatter = biome.getBiomeSkyScatter();

        if (biomeSkyScatter.size() == 1) {
            return biome.getBiomeSkyScatterResolved().get(0);
        }

        if (biomeSkyScatter.isEmpty()) {
            return getGroundBiome(biome, rng, engine, x, y, z);
        }

        return biome.getBiomeSkyScatterResolved().get(biome.getBiomeGenerator(rng, engine).fit(0, biomeSkyScatter.size() - 1, x, y, z));
    }

    static Biome getGroundBiome(IrisBiome biome, RNG rng, Engine engine, double x, double y, double z) {
        KList<String> biomeScatter = biome.getBiomeScatter();

        if (biomeScatter.isEmpty()) {
            return biome.getDerivative();
        }

        if (biomeScatter.size() == 1) {
            return biome.getBiomeScatterResolved().get(0);
        }

        return biome.getBiomeGenerator(rng, engine).fit(biome.getBiomeScatterResolved(), x, y, z);
    }

    static String getSkyBiomeKey(IrisBiome biome, RNG rng, Engine engine, double x, double y, double z) {
        KList<String> biomeSkyScatter = biome.getBiomeSkyScatter();
        String logicalKey;

        if (biomeSkyScatter.size() == 1) {
            logicalKey = namespacedBiomeKey(biomeSkyScatter.get(0));
        } else if (biomeSkyScatter.isEmpty()) {
            return getGroundBiomeKey(biome, rng, engine, x, y, z);
        } else {
            logicalKey = namespacedBiomeKey(biomeSkyScatter.get(
                    biome.getBiomeGenerator(rng, engine).fit(0, biomeSkyScatter.size() - 1, x, y, z)
            ));
        }
        return physicalBiomeKey(engine, logicalKey);
    }

    static String getGroundBiomeKey(IrisBiome biome, RNG rng, Engine engine, double x, double y, double z) {
        KList<String> biomeScatter = biome.getBiomeScatter();
        String logicalKey;

        if (biomeScatter.isEmpty()) {
            logicalKey = biome.getDerivativeKey();
        } else if (biomeScatter.size() == 1) {
            logicalKey = namespacedBiomeKey(biomeScatter.get(0));
        } else {
            logicalKey = namespacedBiomeKey(biomeScatter.get(
                    biome.getBiomeGenerator(rng, engine).fit(0, biomeScatter.size() - 1, x, y, z)
            ));
        }
        return physicalBiomeKey(engine, logicalKey);
    }

    static IrisBiomeCustom getCustomBiome(IrisBiome biome, RNG rng, Engine engine, double x, double y, double z) {
        KList<IrisBiomeCustom> customDerivitives = biome.getCustomDerivitives();

        if (customDerivitives.size() == 1) {
            return customDerivitives.get(0);
        }

        return customDerivitives.get(biome.getBiomeGenerator(rng, engine).fit(0, customDerivitives.size() - 1, x, y, z));
    }

    private static String physicalBiomeKey(Engine engine, String logicalKey) {
        if (engine == null || logicalKey == null) {
            return logicalKey;
        }
        return engine.getData().physicalBiomeResourceKey(engine.getDimension(), logicalKey);
    }
}
