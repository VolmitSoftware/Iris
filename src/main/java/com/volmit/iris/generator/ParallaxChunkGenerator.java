package com.volmit.iris.generator;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDepositGenerator;
import com.volmit.iris.object.IrisObjectPlacement;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.atomics.AtomicSliver;
import com.volmit.iris.object.atomics.AtomicSliverMap;
import com.volmit.iris.object.atomics.AtomicWorldData;
import com.volmit.iris.object.atomics.MasterLock;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.IObjectPlacer;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ParallaxChunkGenerator extends TerrainChunkGenerator implements IObjectPlacer
{
	private KMap<ChunkPosition, AtomicSliver> sliverCache;
	protected AtomicWorldData parallaxMap;
	private KMap<ChunkPosition, AtomicSliver> ceilingSliverCache;
	protected AtomicWorldData ceilingParallaxMap;
	private MasterLock masterLock;
	private ReentrantLock lock = new ReentrantLock();
	private int sliverBuffer;

	public ParallaxChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		sliverCache = new KMap<>();
		ceilingSliverCache = new KMap<>();
		sliverBuffer = 0;
		masterLock = new MasterLock();
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		parallaxMap = new AtomicWorldData(world, "floor");
		ceilingParallaxMap = new AtomicWorldData(world, "ceiling");
	}

	protected KMap<ChunkPosition, AtomicSliver> getSliverCache()
	{
		return getDimension().isInverted() ? ceilingSliverCache : sliverCache;
	}

	protected void onClose()
	{
		super.onClose();

		try
		{
			parallaxMap.unloadAll(true);
			ceilingParallaxMap.unloadAll(true);
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public int getHighest(int x, int z)
	{
		return getHighest(x, z, false);
	}

	@Override
	public int getHighest(int x, int z, boolean ignoreFluid)
	{
		return (int) Math.round(ignoreFluid ? getTerrainHeight(x, z) : getTerrainWaterHeight(x, z));
	}

	@Override
	public void set(int x, int y, int z, BlockData d)
	{
		getMasterLock().lock((x >> 4) + "." + (z >> 4));
		getParallaxSliver(x, z).set(y, d);
		getMasterLock().unlock((x >> 4) + "." + (z >> 4));
	}

	@Override
	public BlockData get(int x, int y, int z)
	{
		BlockData b = sampleSliver(x, z).getBlock().get(y);
		return b == null ? AIR : b;
	}

	@Override
	public boolean isSolid(int x, int y, int z)
	{
		return get(x, y, z).getMaterial().isSolid();
	}

	public AtomicSliver getParallaxSliver(int wx, int wz)
	{
		getMasterLock().lock("gpc");
		getMasterLock().lock((wx >> 4) + "." + (wz >> 4));
		AtomicSliverMap map = getParallaxChunk(wx >> 4, wz >> 4);
		getMasterLock().unlock("gpc");
		AtomicSliver sliver = map.getSliver(wx & 15, wz & 15);
		getMasterLock().unlock((wx >> 4) + "." + (wz >> 4));

		return sliver;
	}

	public boolean isParallaxGenerated(int x, int z)
	{
		return getParallaxChunk(x, z).isParallaxGenerated();
	}

	public boolean isWorldGenerated(int x, int z)
	{
		return getParallaxChunk(x, z).isWorldGenerated();
	}

	public AtomicWorldData getParallaxMap()
	{
		return getDimension().isInverted() ? ceilingParallaxMap : parallaxMap;
	}

	public AtomicSliverMap getParallaxChunk(int x, int z)
	{
		try
		{
			return getParallaxMap().loadChunk(x, z);
		}

		catch(IOException e)
		{
			fail(e);
		}

		return new AtomicSliverMap();
	}

	@Override
	protected void onPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap)
	{
		super.onPostGenerate(random, x, z, data, grid, height, biomeMap);
		getBiomeHitCache().clear();

		if(getDimension().isPlaceObjects())
		{
			onGenerateParallax(random, x, z);
			injectBiomeSky(x, z, grid);
			getParallaxChunk(x, z).inject(data);
			setSliverBuffer(getSliverCache().size());
			getParallaxChunk(x, z).setWorldGenerated(true);
			getSliverCache().clear();
			getMasterLock().clear();
		}

		super.onPostParallaxPostGenerate(random, x, z, data, grid, height, biomeMap);
	}

	protected void injectBiomeSky(int x, int z, BiomeGrid grid)
	{
		if(getDimension().isInverted())
		{
			return;
		}

		int rx;
		int rz;

		for(int i = 0; i < 16; i++)
		{
			rx = (x * 16) + i;
			for(int j = 0; j < 16; j++)
			{
				rz = (z * 16) + j;

				int min = sampleSliver(rx, rz).getHighestBiome();
				int max = getParallaxSliver(rx, rz).getHighestBlock();

				if(min < max)
				{
					IrisBiome biome = getCachedBiome(i, j);

					for(int g = min; g <= max; g++)
					{
						grid.setBiome(i, g, j, biome.getSkyBiome(masterRandom, rz, g, rx));
					}
				}
			}
		}
	}

	protected void onGenerateParallax(RNG random, int x, int z)
	{
		String key = "par." + x + "." + "z";
		ChunkPosition rad = Iris.data.getObjectLoader().getParallaxSize();

		for(int ii = x - (rad.getX() / 2); ii <= x + (rad.getX() / 2); ii++)
		{
			int i = ii;

			for(int jj = z - (rad.getZ() / 2); jj <= z + (rad.getZ() / 2); jj++)
			{
				int j = jj;

				if(isParallaxGenerated(ii, jj))
				{
					continue;
				}

				if(isWorldGenerated(ii, jj))
				{
					continue;
				}

				getAccelerant().queue(key, () ->
				{
					IrisBiome b = sampleTrueBiome((i * 16) + 7, (j * 16) + 7).getBiome();
					IrisRegion r = sampleRegion((i * 16) + 7, (j * 16) + 7);
					RNG ro = random.nextParallelRNG(496888 + i + j);

					int g = 1;

					for(IrisObjectPlacement k : b.getObjects())
					{
						placeObject(k, i, j, random.nextParallelRNG((34 * ((i * 30) + (j * 30) + g++) * i * j) + i - j + 3569222));
					}

					for(IrisDepositGenerator k : getDimension().getDeposits())
					{
						for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
						{
							k.generate((x * 16) + ro.nextInt(16), (z * 16) + ro.nextInt(16), ro, this);
						}
					}

					for(IrisDepositGenerator k : r.getDeposits())
					{
						for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
						{
							k.generate((x * 16) + ro.nextInt(16), (z * 16) + ro.nextInt(16), ro, this);
						}
					}

					for(IrisDepositGenerator k : b.getDeposits())
					{
						for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
						{
							k.generate((x * 16) + ro.nextInt(16), (z * 16) + ro.nextInt(16), ro, this);
						}
					}
				});

				getParallaxChunk(ii, jj).setParallaxGenerated(true);
			}
		}

		getAccelerant().waitFor(key);
	}

	public void placeObject(IrisObjectPlacement o, int x, int z, RNG rng)
	{
		for(int i = 0; i < o.getTriesForChunk(rng); i++)
		{
			rng = rng.nextParallelRNG((i * 3 + 8) - 23040);
			o.getSchematic(rng).place((x * 16) + rng.nextInt(16), (z * 16) + rng.nextInt(16), this, o, rng);
		}
	}

	@Override
	protected void onTick(int ticks)
	{
		getParallaxMap().clean(ticks);
		Iris.data.getObjectLoader().clean();
	}

	public AtomicSliver sampleSliver(int x, int z)
	{
		ChunkPosition key = new ChunkPosition(x, z);

		if(getSliverCache().containsKey(key))
		{
			return getSliverCache().get(key);
		}

		AtomicSliver s = new AtomicSliver(x & 15, z & 15);
		onGenerateColumn(x >> 4, z >> 4, x, z, x & 15, z & 15, s, null);
		getSliverCache().put(key, s);

		return s;
	}

	@Override
	public boolean isPreventingDecay()
	{
		return getDimension().isPreventLeafDecay();
	}
}
