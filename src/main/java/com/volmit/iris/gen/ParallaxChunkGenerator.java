package com.volmit.iris.gen;

import java.io.IOException;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.atomics.AtomicSliver;
import com.volmit.iris.gen.atomics.AtomicSliverMap;
import com.volmit.iris.gen.atomics.AtomicWorldData;
import com.volmit.iris.gen.atomics.MasterLock;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeMutation;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.IrisStructurePlacement;
import com.volmit.iris.object.TileResult;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.IObjectPlacer;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.IrisStructureResult;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.NastyRunnable;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ParallaxChunkGenerator extends TerrainChunkGenerator implements IObjectPlacer {
	protected KMap<ChunkPosition, AtomicSliver> sliverCache;
	protected AtomicWorldData parallaxMap;
	protected KMap<ChunkPosition, AtomicSliver> ceilingSliverCache;
	protected AtomicWorldData ceilingParallaxMap;
	private MasterLock masterLock;
	private IrisLock lock = new IrisLock("ParallaxLock");
	private IrisLock lockq = new IrisLock("ParallaxQueueLock");
	private int sliverBuffer;

	public ParallaxChunkGenerator(String dimensionName, int threads) {
		super(dimensionName, threads);
		sliverCache = new KMap<>();
		ceilingSliverCache = new KMap<>();
		sliverBuffer = 0;
		masterLock = new MasterLock();
	}

	public void onInit(World world, RNG rng) {
		super.onInit(world, rng);
		parallaxMap = new AtomicWorldData(world, "floor");
		ceilingParallaxMap = new AtomicWorldData(world, "ceiling");
	}

	protected KMap<ChunkPosition, AtomicSliver> getSliverCache() {
		return getDimension().isInverted() ? ceilingSliverCache : sliverCache;
	}

	protected void onClose() {
		super.onClose();

		try {
			parallaxMap.unloadAll(true);
			ceilingParallaxMap.unloadAll(true);
		}

		catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public int getHighest(int x, int z) {
		return getHighest(x, z, false);
	}

	@Override
	public int getHighest(int x, int z, boolean ignoreFluid) {
		int h = (int) Math.round(ignoreFluid ? getTerrainHeight(x, z) : getTerrainWaterHeight(x, z));

		if (getDimension().isCarving() && h >= getDimension().getCarvingMin()) {
			while (getGlCarve().isCarved(x, h, z)) {
				h--;
			}

			return h;
		}

		return h;
	}

	@Override
	public void set(int x, int y, int z, BlockData d) {
		getMasterLock().lock((x >> 4) + "." + (z >> 4));
		getParallaxSliver(x, z).set(y, d);
		getMasterLock().unlock((x >> 4) + "." + (z >> 4));
	}

	@Override
	public BlockData get(int x, int y, int z) {
		BlockData b = sampleSliver(x, z).getBlock().get(y);
		return b == null ? AIR : b;
	}

	@Override
	public boolean isSolid(int x, int y, int z) {
		return get(x, y, z).getMaterial().isSolid();
	}

	public AtomicSliver getParallaxSliver(int wx, int wz) {
		getMasterLock().lock("gpc");
		getMasterLock().lock((wx >> 4) + "." + (wz >> 4));
		AtomicSliverMap map = getParallaxChunk(wx >> 4, wz >> 4);
		getMasterLock().unlock("gpc");
		AtomicSliver sliver = map.getSliver(wx & 15, wz & 15);
		getMasterLock().unlock((wx >> 4) + "." + (wz >> 4));

		return sliver;
	}

	public boolean isParallaxGenerated(int x, int z) {
		return getParallaxChunk(x, z).isParallaxGenerated();
	}

	public boolean isWorldGenerated(int x, int z) {
		return getParallaxChunk(x, z).isWorldGenerated();
	}

	public AtomicWorldData getParallaxMap() {
		return getDimension().isInverted() ? ceilingParallaxMap : parallaxMap;
	}

	public AtomicSliverMap getParallaxChunk(int x, int z) {
		try {
			return getParallaxMap().loadChunk(x, z);
		}

		catch (IOException e) {
			fail(e);
		}

		return new AtomicSliverMap();
	}

	@Override
	protected void onPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height,
			BiomeMap biomeMap) {
		if (getSliverCache().size() > 20000) {
			getSliverCache().clear();
		}

		super.onPostGenerate(random, x, z, data, grid, height, biomeMap);
		PrecisionStopwatch p = PrecisionStopwatch.start();

		if (getDimension().isPlaceObjects()) {
			onGenerateParallax(random, x, z);
			getParallaxChunk(x, z).inject(data);
			setSliverBuffer(getSliverCache().size());
			getParallaxChunk(x, z).setWorldGenerated(true);
			getMasterLock().clear();
		}

		p.end();
		getMetrics().getParallax().put(p.getMilliseconds());
		super.onPostParallaxPostGenerate(random, x, z, data, grid, height, biomeMap);
		getParallaxMap().clean(ticks);
		getData().getObjectLoader().clean();
	}

	public IrisStructureResult getStructure(int x, int y, int z) {
		IrisBiome b = sampleTrueBiome(x, z).getBiome();
		IrisRegion r = sampleRegion(x, z);
		RNG ro = getMasterRandom().nextParallelRNG(496888 + (x >> 4) + (z >> 4));
		int h = (int) Math.round(getTerrainHeight(x, z));
		KList<IrisStructurePlacement> p = new KList<>();

		for (IrisStructurePlacement i : r.getStructures()) {
			if (i.getHeight() > -1) {
				if (y >= i.getHeight() && y <= i.getHeight()
						+ (i.getStructure(this).getGridHeight() * i.getStructure(this).getMaxLayers())) {
					p.add(i);
				}
			}

			else if (y >= h && y <= i.getStructure(this).getGridHeight() + h) {
				p.add(i);
			}
		}

		for (IrisStructurePlacement i : b.getStructures()) {
			if (i.getHeight() > -1) {
				if (y >= i.getHeight() && y <= i.getHeight()
						+ (i.getStructure(this).getGridHeight() * i.getStructure(this).getMaxLayers())) {
					p.add(i);
				}
			}

			else if (y >= h && y <= i.getStructure(this).getGridHeight() + h) {
				p.add(i);
			}
		}

		for (IrisStructurePlacement i : p) {
			if (!i.hasStructure(ro, x, y, z)) {
				continue;
			}

			int hv = (i.getHeight() == -1 ? 0 : i.getHeight())
					+ (Math.floorDiv(y, i.getStructure(this).getGridHeight()) * i.getStructure(this).getGridHeight());
			TileResult tile = i.getStructure(this).getTile(ro, Math.floorDiv(i.gridSize(this), x) * i.gridSize(this),
					hv, Math.floorDiv(i.gridSize(this), z) * i.gridSize(this));

			if (tile != null && tile.getTile() != null) {
				return new IrisStructureResult(tile.getTile(), i.getStructure(this));
			}
		}

		return null;
	}

	protected void onGenerateParallax(RNG random, int x, int z) {
		String key = "par." + x + "." + "z";
		ChunkPosition rad = getDimension().getParallaxSize(this);
		KList<NastyRunnable> q = new KList<>();

		for (int ii = x - (rad.getX() / 2); ii <= x + (rad.getX() / 2); ii++) {
			int i = ii;

			for (int jj = z - (rad.getZ() / 2); jj <= z + (rad.getZ() / 2); jj++) {
				int j = jj;

				if (isParallaxGenerated(ii, jj)) {
					continue;
				}

				if (isWorldGenerated(ii, jj)) {
					continue;
				}

				getAccelerant().queue(key, () -> {
					IrisBiome b = sampleTrueBiome((i * 16) + 7, (j * 16) + 7).getBiome();
					RNG ro = getMasterRandom().nextParallelRNG(496888 + i + j);

					int g = 1;

					searching: for (IrisBiomeMutation k : getDimension().getMutations()) {
						for (int l = 0; l < k.getChecks(); l++) {
							IrisBiome sa = sampleTrueBiome(
									((i * 16) + ro.nextInt(16)) + ro.i(-k.getRadius(), k.getRadius()),
									((j * 16) + ro.nextInt(16)) + ro.i(-k.getRadius(), k.getRadius())).getBiome();
							IrisBiome sb = sampleTrueBiome(
									((i * 16) + ro.nextInt(16)) + ro.i(-k.getRadius(), k.getRadius()),
									((j * 16) + ro.nextInt(16)) + ro.i(-k.getRadius(), k.getRadius())).getBiome();

							if (sa.getLoadKey().equals(sb.getLoadKey())) {
								continue;
							}

							if (k.getRealSideA(this).contains(sa.getLoadKey())
									&& k.getRealSideB(this).contains(sb.getLoadKey())) {
								for (IrisObjectPlacement m : k.getObjects()) {
									int gg = g++;
									lockq.lock();
									q.add(() -> {
										placeObject(m, i, j, random.nextParallelRNG(
												(34 * ((i * 30) + (j * 30) + gg) * i * j) + i - j + 1569962));
									});
									lockq.unlock();
								}

								continue searching;
							}
						}
					}

					IrisRegion r = sampleRegion((i * 16) + 7, (j * 16) + 7);

					for (IrisStructurePlacement k : r.getStructures()) {
						lockq.lock();
						q.add(() -> {
							k.place(this, random.nextParallelRNG(2228), i, j);
						});
						lockq.unlock();
					}

					for (IrisStructurePlacement k : b.getStructures()) {
						lockq.lock();
						q.add(() -> {
							k.place(this, random.nextParallelRNG(-22228), i, j);
						});
						lockq.unlock();
					}

					for (IrisObjectPlacement k : b.getObjects()) {
						int gg = g++;
						lockq.lock();
						q.add(() -> {
							placeObject(k, i, j, random
									.nextParallelRNG((34 * ((i * 30) + (j * 30) + gg) * i * j) + i - j + 3569222));
						});
						lockq.unlock();
					}

					if (getDimension().isCaves()) {
						int bx = (i * 16) + ro.nextInt(16);
						int bz = (j * 16) + ro.nextInt(16);

						IrisBiome biome = sampleCaveBiome(bx, bz).getBiome();

						if (biome == null) {
							return;
						}

						if (biome.getObjects().isEmpty()) {
							return;
						}

						for (IrisObjectPlacement k : biome.getObjects()) {
							int gg = g++;
							lockq.lock();
							q.add(() -> {
								placeCaveObject(k, i, j, random
										.nextParallelRNG((34 * ((i * 30) + (j * 30) + gg) * i * j) + i - j + 1869322));
							});
							lockq.unlock();
						}
					}
				});

				getParallaxChunk(ii, jj).setParallaxGenerated(true);
			}
		}

		getAccelerant().waitFor(key);

		lockq.lock();
		for (NastyRunnable i : q) {
			getAccelerant().queue(key + "-obj", i);
		}
		lockq.unlock();

		getAccelerant().waitFor(key + "-obj");
	}

	public void placeObject(IrisObjectPlacement o, int x, int z, RNG rng) {
		for (int i = 0; i < o.getTriesForChunk(rng); i++) {
			rng = rng.nextParallelRNG((i * 3 + 8) - 23040);
			o.getSchematic(this, rng).place((x * 16) + rng.nextInt(16), (z * 16) + rng.nextInt(16), this, o, rng);
		}
	}

	public void placeCaveObject(IrisObjectPlacement o, int x, int z, RNG rng) {
		for (int i = 0; i < o.getTriesForChunk(rng); i++) {
			rng = rng.nextParallelRNG((i * 3 + 8) - 23040);
			int xx = (x * 16) + rng.nextInt(16);
			int zz = (z * 16) + rng.nextInt(16);
			KList<CaveResult> res = getCaves(xx, zz);

			if (res.isEmpty()) {
				continue;
			}

			o.getSchematic(this, rng).place(xx,
					res.get(rng.nextParallelRNG(29345 * (i + 234)).nextInt(res.size())).getFloor() + 2, zz, this, o,
					rng);
		}
	}

	public AtomicSliver sampleSliver(int x, int z) {
		ChunkPosition key = new ChunkPosition(x, z);

		if (getSliverCache().containsKey(key)) {
			return getSliverCache().get(key);
		}

		AtomicSliver s = new AtomicSliver(x & 15, z & 15);
		onGenerateColumn(x >> 4, z >> 4, x, z, x & 15, z & 15, s, null, true);
		getSliverCache().put(key, s);

		return s;
	}

	@Override
	public boolean isPreventingDecay() {
		return getDimension().isPreventLeafDecay();
	}
}
