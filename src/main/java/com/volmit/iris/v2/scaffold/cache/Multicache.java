package com.volmit.iris.v2.scaffold.cache;

import com.volmit.iris.util.V;

public interface Multicache
{
    @SuppressWarnings("hiding")
	public <V> Cache<V> getCache(int id);

    @SuppressWarnings("hiding")
	public <V> Cache<V> createCache();
}
	