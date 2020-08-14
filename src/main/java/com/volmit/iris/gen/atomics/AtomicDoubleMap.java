package com.volmit.iris.gen.atomics;

import com.google.common.util.concurrent.AtomicDoubleArray;

public class AtomicDoubleMap {
	private final AtomicDoubleArray data;

	public AtomicDoubleMap() {
		data = new AtomicDoubleArray(256);
	}

	public double get(int x, int z) {
		return data.get((z << 4) | x);
	}
	
	public int getInt(int x, int z) {
		return (int) Math.round(get(x, z));
	}

	public void set(int x, int z, double v) {
		data.set((z << 4) | x, v);
	}
}
