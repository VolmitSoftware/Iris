package ninja.bytecode.iris.generator;

import java.io.IOException;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisObjectPlacement;
import ninja.bytecode.iris.object.atomics.AtomicSliver;
import ninja.bytecode.iris.object.atomics.AtomicSliverMap;
import ninja.bytecode.iris.object.atomics.AtomicWorldData;
import ninja.bytecode.iris.object.atomics.MasterLock;
import ninja.bytecode.iris.util.BiomeMap;
import ninja.bytecode.iris.util.ChunkPosition;
import ninja.bytecode.iris.util.HeightMap;
import ninja.bytecode.iris.util.IObjectPlacer;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KMap;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ParallaxChunkGenerator extends TerrainChunkGenerator implements IObjectPlacer
{
	private KMap<ChunkPosition, AtomicSliver> sliverCache;
	protected AtomicWorldData parallaxMap;
	private MasterLock masterLock;
	private int sliverBuffer;

	public ParallaxChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		sliverCache = new KMap<>();
		sliverBuffer = 0;
		masterLock = new MasterLock();
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		parallaxMap = new AtomicWorldData(world);
	}

	protected void onClose()
	{
		super.onClose();

		try
		{
			parallaxMap.unloadAll(true);
		}

		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public int getHighest(int x, int z)
	{
		return sampleSliver(x, z).getHighestBlock();
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
		if(getDimension().isPlaceObjects())
		{
			onGenerateParallax(random, x, z);
			getParallaxChunk(x, z).inject(data);
			setSliverBuffer(getSliverCache().size());
			getParallaxChunk(x, z).setWorldGenerated(true);
			getParallaxMap().clean(x + z);
			getSliverCache().clear();
			getMasterLock().clear();
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

				getTx().queue(key, () ->
				{
					IrisBiome b = sampleBiome((i * 16) + 7, (j * 16) + 7).getBiome();
					int g = 1;

					for(IrisObjectPlacement k : b.getObjects())
					{
						placeObject(k, i, j, random.nextParallelRNG((34 * ((i * 30) + (j * 30) + g++) * i * j) + i - j + 3569222));
					}
				});

				getParallaxChunk(ii, jj).setParallaxGenerated(true);
			}
		}

		getTx().waitFor(key);
	}

	protected void placeObject(IrisObjectPlacement o, int x, int z, RNG rng)
	{
		for(int i = 0; i < o.getTriesForChunk(rng); i++)
		{
			rng = rng.nextParallelRNG((i * 3 + 8) - 23040);
			o.getSchematic(rng).place((x * 16) + rng.nextInt(16), (z * 16) + rng.nextInt(16), this, o, rng);
		}
	}

	public AtomicSliver sampleSliver(int x, int z)
	{
		ChunkPosition key = new ChunkPosition(x, z);

		if(sliverCache.containsKey(key))
		{
			return sliverCache.get(key);
		}

		AtomicSliver s = new AtomicSliver(x & 15, z & 15);
		onGenerateColumn(x >> 4, z >> 4, x, z, x & 15, z & 15, s, null);
		sliverCache.put(key, s);

		return s;
	}
}
