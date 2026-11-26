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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.BiomeRuntime;
import art.arcane.iris.generation.biome.FloatingChildRuntime;
import art.arcane.iris.generation.biome.FloatingIslandMemo;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.generation.biome.PaletteLayerRuntime;
import art.arcane.iris.generation.noise.ExpressionRuntime;
import art.arcane.iris.generation.noise.GeneratorRuntime;
import art.arcane.iris.generation.noise.IrisExpression;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisNoiseGenerator;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

import java.util.Objects;

/**
 * The runtime objects one owner derives from pack objects: seeded generators, samplers and profiles. Pack objects are
 * read-only configuration, so everything built for a seed, an engine or a world lives here and dies with the owner.
 * An engine owns one per generation runtime; engine-less tools use the scope their {@link IrisData} owns.
 */
public final class PackRuntime {
    private static final int STYLE_SEED_CAPACITY = 8;
    private static final int NOISE_LAYER_SEED_CAPACITY = 32;
    private static final int MATERIAL_PALETTE_SEED_CAPACITY = 32;
    private static final int CARVING_CHILD_SEED_CAPACITY = 8;

    private final Engine engine;
    private final IrisData data;
    private final RuntimeTable<IrisData, PackRuntime> foreignDataScopes;
    private final RuntimeTable<IrisGeneratorStyle, SeedTable<CNG>> styleGenerators = new RuntimeTable<>(
            style -> new SeedTable<>(STYLE_SEED_CAPACITY, seed -> style.createNoCache(new RNG(seed), this)));
    private final RuntimeTable<IrisNoiseGenerator, SeedTable<CNG>> noiseLayerGenerators = new RuntimeTable<>(
            layer -> new SeedTable<>(NOISE_LAYER_SEED_CAPACITY, seed -> layer.buildGenerator(seed, this)));
    private final RuntimeTable<IrisMaterialPalette, SeedTable<CNG>> materialPaletteGenerators = new RuntimeTable<>(
            palette -> new SeedTable<>(MATERIAL_PALETTE_SEED_CAPACITY, seed -> palette.createLayerGenerator(seed, this)));
    private final RuntimeTable<IrisDimensionCarvingEntry, SeedTable<CNG>> carvingChildGenerators = new RuntimeTable<>(
            entry -> new SeedTable<>(CARVING_CHILD_SEED_CAPACITY, seed -> entry.createChildrenGenerator(seed, this)));
    private final RuntimeTable<IrisGenerator, GeneratorRuntime> generators = new RuntimeTable<>(GeneratorRuntime::new);
    private final RuntimeTable<IrisExpression, ExpressionRuntime> expressions =
            new RuntimeTable<>(expression -> new ExpressionRuntime(expression, this));
    private final RuntimeTable<IrisBiome, BiomeRuntime> biomes = new RuntimeTable<>(biome -> new BiomeRuntime(biome, this));
    private final RuntimeTable<IrisBiomePaletteLayer, PaletteLayerRuntime> paletteLayers =
            new RuntimeTable<>(layer -> new PaletteLayerRuntime(layer, this));
    private final RuntimeTable<IrisFloatingChildBiomes, FloatingChildRuntime> floatingChildren =
            new RuntimeTable<>(entry -> new FloatingChildRuntime(entry, this));
    private final ThreadSlots<FloatingIslandMemo> floatingIslandMemos = new ThreadSlots<>(FloatingIslandMemo::new);

    private PackRuntime(Engine engine, IrisData data) {
        this.engine = engine;
        this.data = data;
        this.foreignDataScopes = engine == null ? null : new RuntimeTable<>(foreign -> new PackRuntime(engine, foreign));
    }

    /** The scope an engine owns for its pack; the engine creates it with each generation runtime. */
    public static PackRuntime forEngine(Engine engine, IrisData data) {
        return new PackRuntime(Objects.requireNonNull(engine, "engine"), data);
    }

    /** A scope for tools without an engine. A null pack is allowed for content that never loads other files. */
    public static PackRuntime standalone(IrisData data) {
        return new PackRuntime(null, data);
    }

    /**
     * The runtime scope for work on this pack: the engine's while one is generating, otherwise the scope the pack
     * data owns. Resolve it once per chunk or column and pass it down.
     */
    public static PackRuntime of(Engine engine, IrisData data) {
        PackRuntime engineRuntime = engine == null ? null : engine.getPackRuntime();
        if (engineRuntime == null) {
            return forPack(data);
        }
        return engineRuntime.scopeFor(data);
    }

    /** The scope a pack keeps for content baked from its own files alone, such as procedural object variants. */
    public static PackRuntime forPack(IrisData data) {
        return data == null ? standalone(null) : data.standalonePackRuntime();
    }

    /** The engine's scope for its own pack. */
    public static PackRuntime of(Engine engine) {
        return of(engine, engine.getData());
    }

    /**
     * The scope for a pack object whose class still finds its engine through the pack data's context. Each use is a
     * caller that has not been handed its runtime scope yet.
     */
    public static PackRuntime forContext(IrisData data) {
        return of(data == null ? null : data.getEngine(), data);
    }

    /** The owning engine, or null for an engine-less scope. */
    public Engine engine() {
        return engine;
    }

    public IrisData data() {
        return data;
    }

    /**
     * The scope this owner keeps for another pack, such as a stacked dimension's. Pack objects resolve expressions
     * and images through their own data, so runtime objects of a foreign pack must be built against it.
     */
    public PackRuntime scopeFor(IrisData packData) {
        if (packData == null || packData == data || foreignDataScopes == null) {
            return this;
        }
        return foreignDataScopes.get(packData);
    }

    /** Styles are shared configuration that each caller seeds itself, so a style keeps one generator per seed. */
    public CNG styleGenerator(IrisGeneratorStyle style, long seed) {
        return styleGenerators.get(style).get(seed);
    }

    /** Noise layers are seeded from the generator that reads them, so a layer keeps one generator per seed. */
    public CNG noiseLayerGenerator(IrisNoiseGenerator layer, long seed) {
        return noiseLayerGenerators.get(layer).get(seed);
    }

    /** Material palettes are seeded by the stream or decorator that reads them, so a palette keeps one generator per seed. */
    public CNG materialPaletteGenerator(IrisMaterialPalette palette, long seed) {
        return materialPaletteGenerators.get(palette).get(seed);
    }

    /** Carving entries pick their children from the carve seed, so an entry keeps one child generator per seed. */
    public CNG carvingChildGenerator(IrisDimensionCarvingEntry entry, long seed) {
        return carvingChildGenerators.get(entry).get(seed);
    }

    public GeneratorRuntime generator(IrisGenerator generator) {
        return generators.get(generator);
    }

    public ExpressionRuntime expression(IrisExpression expression) {
        return expressions.get(expression);
    }

    public BiomeRuntime biome(IrisBiome biome) {
        return biomes.get(biome);
    }

    public PaletteLayerRuntime paletteLayer(IrisBiomePaletteLayer layer) {
        return paletteLayers.get(layer);
    }

    public FloatingChildRuntime floatingChild(IrisFloatingChildBiomes entry) {
        return floatingChildren.get(entry);
    }

    /** The calling worker's floating island samples for its current chunk. */
    public FloatingIslandMemo floatingIslandMemo() {
        return floatingIslandMemos.get();
    }
}
