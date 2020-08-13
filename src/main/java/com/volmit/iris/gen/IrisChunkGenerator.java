package com.volmit.iris.gen;

import java.io.IOException;
import java.lang.reflect.Method;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.volmit.iris.Iris;
import com.volmit.iris.IrisContext;
import com.volmit.iris.gen.atomics.AtomicRegionData;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisEffect;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisChunkGenerator extends CeilingChunkGenerator implements IrisContext {
	private Method initLighting;
	private IrisLock lock;
	private KMap<Player, IrisBiome> b = new KMap<>();

	public IrisChunkGenerator(String dimensionName, int threads) {
		super(dimensionName, threads);
		lock = new IrisLock("IrisChunkGenerator");
	}

	public IrisChunkGenerator(String dimensionName) {
		super(dimensionName, 16);
		lock = new IrisLock("IrisChunkGenerator");
	}

	public IrisChunkGenerator(int tc) {
		super("", tc);
		lock = new IrisLock("IrisChunkGenerator");
	}

	public void hotload() {
		onHotload();
	}

	public void retry() {
		if (failing) {
			failing = false;
			hotload();
		}
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid) {
		lock.lock();
		super.onGenerate(random, x, z, data, grid);
		lock.unlock();
	}

	public void onInit(World world, RNG rng) {
		try {
			super.onInit(world, rng);
		}

		catch (Throwable e) {
			fail(e);
		}
	}

	@Override
	public BiomeResult getBiome(int x, int z) {
		return sampleBiome(x, z);
	}

	@Override
	public IrisRegion getRegion(int x, int z) {
		return sampleRegion(x, z);
	}

	@Override
	public int getHeight(int x, int z) {
		return sampleHeight(x, z);
	}

	@Override
	public void onTick(int ticks) {
		super.onTick(ticks);
		for (Player i : getWorld().getPlayers()) {
			Location l = i.getLocation();
			IrisRegion r = sampleRegion(l.getBlockX(), l.getBlockZ());
			IrisBiome b = sampleTrueBiome(l.getBlockX(), l.getBlockY(), l.getBlockZ()).getBiome();

			for (IrisEffect j : r.getEffects()) {
				j.apply(i, this);
			}

			for (IrisEffect j : b.getEffects()) {
				j.apply(i, this);
			}
		}
	}

	@Override
	protected void onClose() {
		super.onClose();

		try {
			parallaxMap.saveAll();
			ceilingParallaxMap.saveAll();
			parallaxMap.getLoadedChunks().clear();
			parallaxMap.getLoadedRegions().clear();
			ceilingParallaxMap.getLoadedChunks().clear();
			ceilingParallaxMap.getLoadedRegions().clear();
		}

		catch (IOException e) {
			e.printStackTrace();
		}

		setBiomeCache(null);
		setAvailableFilters(null);
		setBiomeHitCache(null);
		setCacheTrueBiome(null);
		setCacheHeightMap(null);
		setCeilingSliverCache(null);
		setSliverCache(null);
		Iris.info("Closing Iris Dimension " + getWorld().getName());
	}

	@Override
	protected void onFailure(Throwable e) {

	}

	@Override
	protected void onChunkLoaded(Chunk c) {

	}

	@Override
	protected void onChunkUnloaded(Chunk c) {

	}

	@Override
	protected void onPlayerJoin(Player p) {

	}

	@Override
	public void onPlayerLeft(Player p) {
		super.onPlayerLeft(p);
	}

	@Override
	public void onHotloaded() {
		CNG.creates = 0;
		getData().dump();
		onHotload();
	}

	public long guessMemoryUsage() {
		long bytes = 1024 * 1024 * (8 + (getThreads() / 3));

		for (AtomicRegionData i : parallaxMap.getLoadedRegions().values()) {
			bytes += i.guessMemoryUsage();
		}

		for (AtomicRegionData i : ceilingParallaxMap.getLoadedRegions().values()) {
			bytes += i.guessMemoryUsage();
		}

		bytes += parallaxMap.getLoadedChunks().size() * 256 * 4 * 460;
		bytes += ceilingParallaxMap.getLoadedChunks().size() * 256 * 4 * 460;
		bytes += getSliverBuffer() * 220;
		bytes += 823 * getData().getObjectLoader().getTotalStorage();

		return bytes;
	}

	@Override
	public boolean shouldGenerateCaves() {
		return false;
	}

	@Override
	public boolean shouldGenerateDecorations() {
		return false;
	}

	@Override
	public boolean shouldGenerateMobs() {
		return true;
	}

	@Override
	public boolean shouldGenerateStructures() {
		if (!isInitialized()) {
			return false;
		}

		return getDimension().isVanillaStructures();
	}
}
