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

package art.arcane.iris.generation.noise;

import art.arcane.iris.generation.runtime.IrisEngineStreamType;
import art.arcane.iris.generation.runtime.IrisEngineValueType;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.cache.LazyBoundedCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

@Snippet("expression-load")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents a variable to use in your expression. Do not set the name to x, y, or z, also don't duplicate names.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisExpressionLoad {
    private static final int STYLE_CACHE_SIZE = 8;
    @Required
    @Description("The variable to assign this value to. Do not set the name to x, y, or z")
    private String name = "";

    @Description("If the style value is not defined, this value will be used")
    private double staticValue = -1;

    @Description("If defined, this variable will use a generator style as it's value")
    private IrisGeneratorStyle styleValue = null;

    @Description("If defined, iris will use an internal stream from the engine as it's value")
    private IrisEngineStreamType engineStreamValue = null;

    @Description("If defined, iris will use an internal value from the engine as it's value")
    private IrisEngineValueType engineValue = null;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient final Map<Engine, EngineCache> engineCaches =
            Collections.synchronizedMap(new IdentityHashMap<>());
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient final LazyBoundedCache<StandaloneStyleKey, CNG> standaloneStyleCache =
            new LazyBoundedCache<>(STYLE_CACHE_SIZE);

    public double getValue(RNG rng, IrisData data, double x, double z) {
        if (engineValue != null) {
            Engine engine = requireEngine(data);
            return cacheFor(engine).value.aquire(() -> engineValue.get(engine));
        }

        if (engineStreamValue != null) {
            Engine engine = requireEngine(data);
            return cacheFor(engine).stream.aquire(() -> engineStreamValue.get(engine)).get(x, z);
        }

        if (styleValue != null) {
            return style(data, rng).noise(x, z);
        }

        return staticValue;
    }

    public double getValue(RNG rng, IrisData data, double x, double y, double z) {
        if (engineValue != null) {
            Engine engine = requireEngine(data);
            return cacheFor(engine).value.aquire(() -> engineValue.get(engine));
        }

        if (engineStreamValue != null) {
            Engine engine = requireEngine(data);
            return cacheFor(engine).stream.aquire(() -> engineStreamValue.get(engine)).get(x, z);
        }

        if (styleValue != null) {
            return style(data, rng).noise(x, y, z);
        }

        return staticValue;
    }

    private Engine requireEngine(IrisData data) {
        Engine engine = data.getEngine();
        if (engine == null) {
            throw new IllegalStateException("Expression variable '" + name + "' requires an active Iris engine.");
        }
        return engine;
    }

    private EngineCache cacheFor(Engine engine) {
        synchronized (engineCaches) {
            Iterator<Map.Entry<Engine, EngineCache>> iterator = engineCaches.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getKey().isClosed()) {
                    iterator.remove();
                }
            }
            EngineCache cache = engineCaches.get(engine);
            if (cache != null) {
                return cache;
            }
            EngineCache created = new EngineCache();
            engineCaches.put(engine, created);
            return created;
        }
    }

    private CNG style(IrisData data, RNG rng) {
        Engine engine = data.getEngine();
        long seed = rng.getSeed();
        if (engine != null) {
            return cacheFor(engine).styles.computeIfAbsent(seed,
                    ignored -> styleValue.createNoCache(new RNG(seed), data));
        }
        StandaloneStyleKey key = new StandaloneStyleKey(data, seed);
        return standaloneStyleCache.computeIfAbsent(key,
                ignored -> styleValue.createNoCache(new RNG(seed), data));
    }

    private static final class EngineCache {
        private final AtomicCache<ProceduralStream<Double>> stream = new AtomicCache<>();
        private final AtomicCache<Double> value = new AtomicCache<>();
        private final LazyBoundedCache<Long, CNG> styles = new LazyBoundedCache<>(STYLE_CACHE_SIZE);
    }

    private static final class StandaloneStyleKey {
        private final IrisData data;
        private final long seed;

        private StandaloneStyleKey(IrisData data, long seed) {
            this.data = data;
            this.seed = seed;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StandaloneStyleKey key)) {
                return false;
            }
            return data == key.data && seed == key.seed;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(data) + Long.hashCode(seed);
        }
    }
}
