package com.volmit.iris.scaffold.cache;

public interface Multicache {
    @SuppressWarnings("hiding")
    <V> Cache<V> getCache(int id);

    @SuppressWarnings("hiding")
    <V> Cache<V> createCache();
}
	