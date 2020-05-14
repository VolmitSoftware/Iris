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
	private int sliverBuffer = 0;

	public ParallaxChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
		sliverCache = new KMap<>();
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
		getParallaxSliver(x, z).set(y, d);
	}

	@Override
	public BlockData get(int x, int y, int z)
	{
		BlockData b = sampleSliver(x, z).getBlock().get(y);
		return b == null ? AIR : b;
	}

	public AtomicSliver getParallaxSliver(int wx, int wz)
	{
		return getParallaxChunk(wx >> 4, wz >> 4).getSliver(wx & 15, wz & 15);
	}

	public boolean hasParallaxChunk(int x, int z)
	{
		try
		{
			return getParallaxMap().hasChunk(x, z);
		}

		catch(IOException e)
		{
			fail(e);
		}

		return false;
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
		onGenerateParallax(random, x, z);
		getParallaxChunk(x, z).inject(data);
		sliverBuffer = sliverCache.size();
		sliverCache.clear();
	}

	protected void onGenerateParallax(RNG random, int x, int z)
	{
		ChunkPosition pos = Iris.data.getObjectLoader().getParallaxSize();

		for(int i = x - pos.getX() / 2; i <= x + pos.getX() / 2; i++)
		{
			for(int j = z - pos.getZ() / 2; j <= z + pos.getZ() / 2; j++)
			{
				IrisBiome b = sampleBiome((i * 16) + 7, (j * 16) + 7).getBiome();
				int g = 1;

				for(IrisObjectPlacement k : b.getObjects())
				{
					placeObject(k, i, j, random.nextParallelRNG((i * 30) + (j * 30) + g++));
				}
			}
		}
	}

	@Override
	protected void onTick(int ticks)
	{
		if(ticks % 100 == 0)
		{
			parallaxMap.clean();
		}
	}

	protected void placeObject(IrisObjectPlacement o, int x, int z, RNG rng)
	{
		for(int i = 0; i < o.getTriesForChunk(rng); i++)
		{
			o.getSchematic(rng).place((x * 16) * rng.nextInt(16), (z * 16) + rng.nextInt(16), this);
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
