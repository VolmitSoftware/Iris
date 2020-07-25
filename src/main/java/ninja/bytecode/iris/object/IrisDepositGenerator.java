package ninja.bytecode.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import lombok.Data;
import ninja.bytecode.iris.generator.ParallaxChunkGenerator;
import ninja.bytecode.iris.util.BlockDataTools;
import ninja.bytecode.iris.util.Desc;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Data
public class IrisDepositGenerator
{
	@Desc("The minimum height this deposit can generate at")
	private int minHeight = 7;

	@Desc("The maximum height this deposit can generate at")
	private int maxHeight = 55;

	@Desc("The minimum amount of deposit blocks per clump")
	private int minSize = 3;

	@Desc("The maximum amount of deposit blocks per clump")
	private int maxSize = 5;

	@Desc("The maximum amount of clumps per chunk")
	private int maxPerChunk = 3;

	@Desc("The minimum amount of clumps per chunk")
	private int minPerChunk = 1;

	@Desc("The palette of blocks to be used in this deposit generator")
	private KList<String> palette = new KList<String>();

	@Desc("Ore varience is how many different objects clumps iris will create")
	private int varience = 8;

	private transient IrisObjectPlacement config = createDepositConfig();
	private transient ReentrantLock lock = new ReentrantLock();
	private transient KList<IrisObject> objects;
	private transient KList<BlockData> blockData;

	public IrisObject getClump(RNG rng)
	{
		lock.lock();

		if(objects == null)
		{
			RNG rngv = rng.nextParallelRNG(3957778);
			objects = new KList<>();

			for(int i = 0; i < varience; i++)
			{
				objects.add(generateClumpObject(rngv.nextParallelRNG(2349 * i + 3598)));
			}
		}

		lock.unlock();
		return objects.get(rng.i(0, objects.size() - 1));
	}

	private IrisObjectPlacement createDepositConfig()
	{
		IrisObjectPlacement p = new IrisObjectPlacement();
		IrisObjectRotation rot = new IrisObjectRotation();
		rot.setEnabled(true);
		IrisAxisRotationClamp xc = new IrisAxisRotationClamp();
		IrisAxisRotationClamp yc = new IrisAxisRotationClamp();
		IrisAxisRotationClamp zc = new IrisAxisRotationClamp();
		xc.setEnabled(true);
		xc.setInterval(45);
		yc.setEnabled(true);
		yc.setInterval(45);
		zc.setEnabled(true);
		zc.setInterval(45);
		rot.setXAxis(xc);
		rot.setYAxis(yc);
		rot.setZAxis(zc);
		p.setRotation(rot);
		p.setUnderwater(true);

		return p;
	}

	private IrisObject generateClumpObject(RNG rngv)
	{
		int s = rngv.i(minSize, maxSize);
		int dim = (int) Math.round(Math.pow(s, 1D / 3D));
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
		lock.lock();

		if(blockData == null)
		{
			blockData = new KList<>();

			for(String ix : palette)
			{
				BlockData bx = BlockDataTools.getBlockData(ix);

				if(bx != null)
				{
					blockData.add(bx);
				}
			}
		}

		lock.unlock();

		return blockData;
	}

	public void generate(int x, int z, RNG rng, ParallaxChunkGenerator g)
	{
		IrisObject clump = getClump(rng);
		int height = (int) (Math.round(g.getTerrainHeight(x, z))) - 5;

		if(height < 0)
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

		if(h > maxHeight || h < minHeight)
		{
			return;
		}

		clump.place(x, h, z, g, config, rng);
	}
}
