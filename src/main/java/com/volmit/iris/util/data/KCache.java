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

package com.volmit.iris.util.data;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.volmit.iris.engine.framework.MeteredCache;

import java.util.function.Function;

public class KCache<K,V> implements MeteredCache {
    private long max;
    private CacheLoader<K, V> loader;
    private LoadingCache<K, V> cache;

    public KCache(CacheLoader<K, V> loader, long max)
    {
        this.max = max;
        this.loader = loader;
        this.cache = create(loader);
    }

    private LoadingCache<K,V> create(CacheLoader<K,V> loader) {
        return Caffeine
                .newBuilder()
                .maximumSize(max)
                .build((k) -> loader == null ? null : loader.load(k));
    }


    public void setLoader(CacheLoader<K, V> loader)
    {
        this.loader = loader;
    }

    public void invalidate(K k)
    {
        cache.invalidate(k);
    }

    public void invalidate()
    {
        LoadingCache<?,?> c = cache;
        cache = create(loader);
        c.invalidateAll();
    }

    public V get(K k)
    {
        return cache.get(k);
    }

    @Override
    public long getSize() {
        return cache.estimatedSize();
    }

    @Override
    public long getMaxSize() {
        return max;
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    public boolean contains(K next) {
        return cache.getIfPresent(next) != null;
    }
}
