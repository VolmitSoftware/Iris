package com.volmit.iris.gen.atomics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.KMap;

public class AtomicMulticache {
	private final AtomicInteger x;
	private final AtomicInteger z;
	private final KMap<Long, Double> height;
	private final KMap<Long, BiomeResult> biome;
	private final KMap<Long, BiomeResult> rawBiome;
	private final KMap<Long, IrisRegion> region;
	private int r = 0;
	private int w = 0;
	private int m = 0;

	public AtomicMulticache() {
		x = new AtomicInteger(0);
		z = new AtomicInteger(0);
		height = new KMap<Long, Double>();
		biome = new KMap<Long, BiomeResult>();
		rawBiome = new KMap<Long, BiomeResult>();
		region = new KMap<Long, IrisRegion>();
	}

	public void targetChunk(int x, int z) {
		this.x.set(x);
		this.z.set(z);
		height.clear();
		region.clear();
		biome.clear();
		rawBiome.clear();
		r = 0;
		w = 0;
		m = 0;
	}

	public double getHeight(int x, int z, Supplier<Double> g) {
		return height.compute(pos(x, z), (k, v) -> {
			if (v == null) {
				m++;
				w++;
				return g.get();
			}

			r++;

			return v;
		});
	}

	public IrisRegion getRegion(int x, int z, Supplier<IrisRegion> g) {
		return region.compute(pos(x, z), (k, v) -> {
			if (v == null) {
				m++;
				w++;
				return g.get();
			}

			r++;

			return v;
		});
	}

	public BiomeResult getBiome(int x, int z, Supplier<BiomeResult> g) {
		return biome.compute(pos(x, z), (k, v) -> {
			if (v == null) {
				m++;
				w++;
				return g.get();
			}

			r++;

			return v;
		});
	}

	public BiomeResult getRawBiome(int x, int z, Supplier<BiomeResult> g) {
		return rawBiome.compute(pos(x, z), (k, v) -> {
			if (v == null) {
				m++;
				w++;
				return g.get();
			}

			r++;

			return v;
		});
	}

	private long pos(int x, int z) {
		return (((long) x) << 32) | (z & 0xffffffffL);
	}

	public void updateHeight(int x, int z, int h) {
		height.put(pos(x, z), (double) h);
	}

	public double getSize() {
		return height.size();
	}
}
