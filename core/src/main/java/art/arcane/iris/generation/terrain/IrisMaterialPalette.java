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

package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.cache.LazyBoundedCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Snippet("palette")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A palette of materials")
@Data
public class IrisMaterialPalette {
    private static final int LAYER_GENERATOR_CACHE_SIZE = 32;
    private static final int LAYER_GENERATOR_SALT = -23_498_896;

    private final transient AtomicCache<KList<PlatformBlockState>> blockData = new AtomicCache<>();
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient LazyBoundedCache<LayerGeneratorKey, CNG> layerGenerators =
            new LazyBoundedCache<>(LAYER_GENERATOR_CACHE_SIZE);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient AtomicReference<CachedLayerGenerator> recentLayerGenerator = new AtomicReference<>();
    private final transient AtomicCache<CNG> heightGenerator = new AtomicCache<>();
    @Description("The style of noise")
    private IrisGeneratorStyle style = NoiseStyle.STATIC.style();
    @MinNumber(0.0001)
    @Description("The terrain zoom mostly for zooming in on a wispy palette")
    private double zoom = 5;
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("The palette of blocks to be used in this layer")
    private KList<IrisBlockData> palette = new KList<IrisBlockData>().qadd(new IrisBlockData("STONE"));

    public PlatformBlockState get(RNG rng, double x, double y, double z, IrisData rdata) {
        KList<PlatformBlockState> localBlockData = getBlockData(rdata);
        int blockDataSize = localBlockData.size();
        if (blockDataSize == 0) {
            return null;
        }

        if (blockDataSize == 1) {
            return localBlockData.get(0);
        }

        double scaledX = x / zoom;
        double scaledY = y / zoom;
        double scaledZ = z / zoom;
        return getLayerGenerator(rng, rdata).fit(localBlockData, scaledX, scaledY, scaledZ);
    }

    public Optional<TileData> getTile(RNG rng, double x, double y, double z, IrisData rdata) {
        if (getBlockData(rdata).isEmpty())
            return Optional.empty();

        TileData tile = getBlockData(rdata).size() == 1 ? palette.get(0).tryGetTile(rdata) : palette.getRandom(rng).tryGetTile(rdata);
        return tile != null ? Optional.of(tile) : Optional.empty();
    }

    public CNG getLayerGenerator(RNG rng, IrisData rdata) {
        Engine engine = rdata == null ? null : rdata.getEngine();
        int generatorSignature = LAYER_GENERATOR_SALT + getBlockData(rdata).size();
        long generatorSeed = rng.getSeed() + generatorSignature;
        CachedLayerGenerator recent = recentLayerGenerator.get();
        if (recent != null && recent.key.matches(rdata, engine, generatorSeed)) {
            return recent.generator;
        }

        LayerGeneratorKey key = new LayerGeneratorKey(rdata, engine, generatorSeed);
        CNG generator = layerGenerators.computeIfAbsent(key,
                ignored -> style.create(new RNG(generatorSeed), rdata, engine));
        if (generator != null) {
            recentLayerGenerator.set(new CachedLayerGenerator(key, generator));
        }
        return generator;
    }

    public IrisMaterialPalette qclear() {
        palette.clear();
        return this;
    }

    public KList<IrisBlockData> add(String b) {
        palette.add(new IrisBlockData(b));

        return palette;
    }

    public IrisMaterialPalette qadd(String b) {
        palette.add(new IrisBlockData(b));

        return this;
    }

    public KList<PlatformBlockState> getBlockData(IrisData rdata) {
        return blockData.aquire(() ->
        {
            KList<PlatformBlockState> blockData = new KList<>();
            for (IrisBlockData ix : palette) {
                PlatformBlockState bx = ix.getBlockData(rdata);
                if (bx != null) {
                    for (int i = 0; i < ix.getWeight(); i++) {
                        blockData.add(bx);
                    }
                }
            }

            return blockData;
        });
    }

    public IrisMaterialPalette zero() {
        palette.clear();
        return this;
    }

    private static final class LayerGeneratorKey {
        private final IrisData data;
        private final Engine engine;
        private final long seed;

        private LayerGeneratorKey(IrisData data, Engine engine, long seed) {
            this.data = data;
            this.engine = engine;
            this.seed = seed;
        }

        private boolean matches(IrisData data, Engine engine, long seed) {
            return this.data == data && this.engine == engine && this.seed == seed;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof LayerGeneratorKey other)) {
                return false;
            }
            return data == other.data && engine == other.engine && seed == other.seed;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(data);
            result = 31 * result + System.identityHashCode(engine);
            result = 31 * result + Long.hashCode(seed);
            return result;
        }
    }

    private static final class CachedLayerGenerator {
        private final LayerGeneratorKey key;
        private final CNG generator;

        private CachedLayerGenerator(LayerGeneratorKey key, CNG generator) {
            this.key = key;
            this.generator = generator;
        }
    }
}
