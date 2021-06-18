package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Creates ore & other block deposits underground")
@Data
public class IrisDepositGenerator
{
	@Required
	@MinNumber(0)
	@MaxNumber(256) // TODO: WARNING HEIGHT
	@DontObfuscate
	@Desc("The minimum height this deposit can generate at")
	private int minHeight = 7;

	@Required
	@MinNumber(0)
	@MaxNumber(256) // TODO: WARNING HEIGHT
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
	@ArrayType(min = 1, type = IrisBlockData.class)
	@DontObfuscate
	@Desc("The palette of blocks to be used in this deposit generator")
	private KList<IrisBlockData> palette = new KList<IrisBlockData>();

	@MinNumber(1)
	@MaxNumber(64)
	@DontObfuscate
	@Desc("Ore varience is how many different objects clumps iris will create")
	private int varience = 3;

	private final transient AtomicCache<KList<IrisObject>> objects = new AtomicCache<>();
	private final transient AtomicCache<KList<BlockData>> blockData = new AtomicCache<>();

	public IrisObject getClump(RNG rng, IrisDataManager rdata)
	{
		KList<IrisObject> objects = this.objects.aquire(() ->
		{
			RNG rngv = rng.nextParallelRNG(3957778);
			KList<IrisObject> objectsf = new KList<>();

			for(int i = 0; i < varience; i++)
			{
				objectsf.add(generateClumpObject(rngv.nextParallelRNG(2349 * i + 3598), rdata));
			}

			return objectsf;
		});
		return objects.get(rng.i(0, objects.size() - 1));
	}

	public int getMaxDimension()
	{
		return Math.min(11, (int) Math.round(Math.pow(maxSize, 1D / 3D)));
	}

	private IrisObject generateClumpObject(RNG rngv, IrisDataManager rdata)
	{
		int s = rngv.i(minSize, maxSize);
		int dim = Math.min(11, (int) Math.round(Math.pow(maxSize, 1D / 3D)));
		int w = dim / 2;
		IrisObject o = new IrisObject(dim, dim, dim);

		if(s == 1)
		{
			o.getBlocks().put(o.getCenter(), nextBlock(rngv, rdata));
		}

		else
		{
			while(s > 0)
			{
				s--;
				BlockVector ang = new BlockVector(rngv.i(-w, w), rngv.i(-w, w), rngv.i(-w, w));
				BlockVector pos = o.getCenter().clone().add(ang).toBlockVector();
				o.getBlocks().put(pos, nextBlock(rngv, rdata));
			}
		}

		return o;
	}

	private BlockData nextBlock(RNG rngv, IrisDataManager rdata)
	{
		return getBlockData(rdata).get(rngv.i(0, getBlockData(rdata).size() - 1));
	}

	public KList<BlockData> getBlockData(IrisDataManager rdata)
	{
		return blockData.aquire(() ->
		{
			KList<BlockData> blockData = new KList<>();

			for(IrisBlockData ix : palette)
			{
				BlockData bx = ix.getBlockData(rdata);

				if(bx != null)
				{
					blockData.add(bx);
				}
			}

			return blockData;
		});
	}
}
