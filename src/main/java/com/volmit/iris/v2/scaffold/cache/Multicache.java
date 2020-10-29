package com.volmit.iris.v2.scaffold.cache;

import com.volmit.iris.util.V;

public interface Multicache
{
    public <V> Cache<V> getCache(int id);

    public <V> Cache<V> createCache();
}
