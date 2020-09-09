package com.volmit.iris.object;

import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.util.BlockVector;

import com.volmit.iris.gen.TopographicTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.B;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.Data;

@Desc("Creates ore & other block deposits underground")
@Data
public class IrisDepositGenerator
{
	@Required
	@MinNumber(0)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The minimum height this deposit can generate at")
	private int minHeight = 7;

	@Required
	@MinNumber(0)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The maximum height this deposit can generate at")
	private int maxHeight = 55;

	@Required
	@MinNumber(1)
	@MaxNumber(32)
	@DontObfuscate
	@Desc("The minimum amount of deposit blocks per clump")
	private int minSize = 3;

	@Required
	@MinNumber(1)
	@MaxNumber(32)
	@DontObfuscate
	@Desc("The maximum amount of deposit blocks per clump")
	private int maxSize = 5;

	@Required
	@MinNumber(1)
	@MaxNumber(128)
	@DontObfuscate
	@Desc("The maximum amount of clumps per chunk")
	private int maxPerChunk = 3;

	@Required
	@MinNumber(0)
	@MaxNumber(128)
	@DontObfuscate
	@Desc("The minimum amount of clumps per chunk")
	private int minPerChunk = 1;

	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("The palette of blocks to be used in this deposit generator")
	private KList<String> palette = new KList<String>();

	@MinNumber(1)
	@MaxNumber(64)
	@DontObfuscate
	@Desc("Ore varience is how many different objects clumps iris will create")
	private int varience = 3;

	private transient AtomicCache<KList<IrisObject>> objects = new AtomicCache<>();
	private transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();

	public IrisObject getClump(RNG rng)
	{
		KList<IrisObject> objects = this.objects.aquire(() ->
		{
			RNG rngv = rng.nextParallelRNG(3957778);
			KList<IrisObject> objectsf = new KList<>();

			for(int i = 0; i < varience; i++)
			{
				objectsf.add(generateClumpObject(rngv.nextParallelRNG(2349 * i + 3598)));
			}

			return objectsf;
		});
		return objects.get(rng.i(0, objects.size() - 1));
	}

	public int getMaxDimension()
	{
		return Math.min(11, (int) Math.round(Math.pow(maxSize, 1D / 3D)));
	}

	private IrisObject generateClumpObject(RNG rngv)
	{
		int s = rngv.i(minSize, maxSize);
		int dim = Math.min(11, (int) Math.round(Math.pow(maxSize, 1D / 3D)));
		int w = dim / 2;
		IrisObject o = new IrisObject(dim, dim, dim);

		if(s == 1)
		{
			o.getBlocks().put(o.getCenter(), nextBlock(rngv));
		}

		else
		{
			while(s > 0)
			{
				s--;
				BlockVector ang = new BlockVector(rngv.i(-w, w), rngv.i(-w, w), rngv.i(-w, w));
				BlockVector pos = o.getCenter().clone().add(ang).toBlockVector();
				o.getBlocks().put(pos, nextBlock(rngv));
			}
		}

		return o;
	}

	private BlockData nextBlock(RNG rngv)
	{
		return getBlockData().get(rngv.i(0, getBlockData().size() - 1));
	}

	public KList<BlockData> getBlockData()
	{
		return blockData.aquire(() ->
		{
			KList<BlockData> blockData = new KList<>();

			for(String ix : palette)
			{
				BlockData bx = B.getBlockData(ix);

				if(bx != null)
				{
					blockData.add(bx);
				}
			}

			return blockData;
		});
	}

	public void generate(ChunkData data, RNG rng, TopographicTerrainProvider g, int cx, int cz)
	{
		for(int l = 0; l < rng.i(getMinPerChunk(), getMaxPerChunk()); l++)
		{
			IrisObject clump = getClump(rng);

			int af = (int) Math.ceil(clump.getW() / 2D);
			int bf = (int) Math.floor(16D - (clump.getW() / 2D));

			if(af > bf || af < 0 || bf > 15 || af > 15 || bf < 0)
			{
				af = 6;
				bf = 9;
			}

			int x = rng.i(af, bf);
			int z = rng.i(af, bf);
			int height = (int) (Math.round(g.getCarvedWaterHeight((cx << 4) + x, (cz << 4) + z))) - 7;

			if(height <= 0)
			{
				return;
			}

			int i = Math.max(0, minHeight);
			int a = Math.min(height, Math.min(256, maxHeight));

			if(i >= a)
			{
				return;
			}

			int h = rng.i(i, a);

			if(h > maxHeight || h < minHeight || h > height - 2)
			{
				return;
			}

			for(BlockVector j : clump.getBlocks().keySet())
			{
				int nx = j.getBlockX() + x;
				int ny = j.getBlockY() + h;
				int nz = j.getBlockZ() + z;

				if(ny > height - 2 || nx > 15 || nx < 0 || ny > 255 || ny < 0 || nz < 0 || nz > 15)
				{
					continue;
				}

				boolean allow = false;
				BlockData b = data.getBlockData(nx, ny, nz);
				for(BlockData f : g.getDimension().getRockData())
				{
					if(f.getMaterial().equals(b.getMaterial()))
					{
						allow = true;
						break;
					}
				}

				if(allow)
				{
					data.setBlock(nx, ny, nz, clump.getBlocks().get(j));
				}
			}
		}
	}
}
