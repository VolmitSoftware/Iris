package com.volmit.iris.generator.legacy.atomics;

import java.util.concurrent.atomic.AtomicReferenceArray;

public class AtomicObjectMap<T> {
	private final AtomicReferenceArray<T> data;

	public AtomicObjectMap() {
		data = new AtomicReferenceArray<T>(256);
	}

	public T get(int x, int z) {
		return data.get((z << 4) | x);
	}

	public void set(int x, int z, T v) {
		data.set((z << 4) | x, v);
	}
}
