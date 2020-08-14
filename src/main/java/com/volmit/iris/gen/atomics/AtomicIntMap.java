package com.volmit.iris.gen.atomics;

import java.util.concurrent.atomic.AtomicIntegerArray;

public class AtomicIntMap {
	private final AtomicIntegerArray data;

	public AtomicIntMap() {
		data = new AtomicIntegerArray(256);
	}

	public int get(int x, int z) {
		return data.get((z << 4) | x);
	}

	public void set(int x, int z, int v) {
		data.set((z << 4) | x, v);
	}
}
