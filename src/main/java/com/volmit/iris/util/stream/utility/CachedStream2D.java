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

package com.volmit.iris.util.stream.utility;

import com.volmit.iris.Iris;
import com.volmit.iris.core.service.PreservationSVC;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.MeteredCache;
import com.volmit.iris.util.data.KCache;
import com.volmit.iris.util.stream.BasicStream;
import com.volmit.iris.util.stream.ProceduralStream;

public class CachedStream2D<T> extends BasicStream<T> implements ProceduralStream<T>, MeteredCache {
    private final ProceduralStream<T> stream;
    private final KCache<Long, T> cache;
    private final Engine engine;

    public CachedStream2D(String name, Engine engine, ProceduralStream<T> stream, int size) {
        super();
        this.stream = stream;
        this.engine = engine;
        cache = new KCache<>(k -> stream.get(Cache.keyX(k), Cache.keyZ(k)), size);
        Iris.service(PreservationSVC.class).registerCache(this);
    }

    @Override
    public double toDouble(T t) {
        return stream.toDouble(t);
    }

    @Override
    public T fromDouble(double d) {
        return stream.fromDouble(d);
    }

    @Override
    public T get(double x, double z) {
        return cache.get(Cache.key((int) x, (int) z));
    }

    @Override
    public T get(double x, double y, double z) {
        return stream.get(x, y, z);
    }

    @Override
    public long getSize() {
        return cache.getSize();
    }

    @Override
    public KCache<?, ?> getRawCache() {
        return cache;
    }

    @Override
    public long getMaxSize() {
        return cache.getMaxSize();
    }

    @Override
    public boolean isClosed() {
        return engine.isClosed();
    }
}
