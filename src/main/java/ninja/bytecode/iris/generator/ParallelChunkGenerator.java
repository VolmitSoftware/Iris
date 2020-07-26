package ninja.bytecode.iris.generator;

import org.bukkit.World;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.object.atomics.AtomicSliver;
import ninja.bytecode.iris.object.atomics.AtomicSliverMap;
import ninja.bytecode.iris.util.BiomeMap;
import ninja.bytecode.iris.util.GroupedExecutor;
import ninja.bytecode.iris.util.HeightMap;
import ninja.bytecode.iris.util.RNG;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ParallelChunkGenerator extends BiomeChunkGenerator
{
	private GroupedExecutor accelerant;
	private int threads;

	public ParallelChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName);
		this.threads = threads;
	}

	public void changeThreadCount(int tc)
	{
		threads = tc;
		GroupedExecutor e = accelerant;
		accelerant = new GroupedExecutor(threads, Thread.NORM_PRIORITY, "Iris Generator - " + world.getName());
		Iris.executors.add(accelerant);

		if(e != null)
		{
			e.close();
		}
	}

	protected abstract void onGenerateColumn(int cx, int cz, int wx, int wz, int x, int z, AtomicSliver sliver, BiomeMap biomeMap);

	protected abstract int onSampleColumnHeight(int cx, int cz, int wx, int wz, int x, int z);

	protected abstract void onPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height, BiomeMap biomeMap);

	protected int sampleHeight(int x, int z)
	{
		return onSampleColumnHeight(x >> 4, z >> 4, x, z, x & 15, z & 15);
	}

	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		AtomicSliverMap map = new AtomicSliverMap();
		HeightMap height = new HeightMap();
		String key = "c" + x + "," + z;
		BiomeMap biomeMap = new BiomeMap();
		int ii, jj;

		for(ii = 0; ii < 16; ii++)
		{
			int i = ii;
			int wx = (x * 16) + i;

			for(jj = 0; jj < 16; jj++)
			{
				int j = jj;
				int wz = (z * 16) + j;
				AtomicSliver sliver = map.getSliver(i, j);

				accelerant.queue(key, () ->
				{
					onGenerateColumn(x, z, wx, wz, i, j, sliver, biomeMap);
				});
			}
		}

		accelerant.waitFor(key);
		map.write(data, grid, height);
		onPostGenerate(random, x, z, data, grid, height, biomeMap);
	}

	protected void onClose()
	{
		accelerant.close();
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		changeThreadCount(threads);
	}

	@Override
	public boolean isParallelCapable()
	{
		return false;
	}
}
