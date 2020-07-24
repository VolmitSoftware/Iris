package ninja.bytecode.iris.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.object.IrisDimension;
import ninja.bytecode.iris.util.InvertedBiomeGrid;
import ninja.bytecode.iris.util.RNG;

public abstract class CeilingChunkGenerator extends ParallaxChunkGenerator
{
	protected boolean generatingCeiling = false;

	public CeilingChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
	}

	@Override
	protected void onGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid)
	{
		targetFloor();
		generate(random, x, z, data, grid);

		if(getFloorDimension().isMirrorCeiling())
		{
			writeInverted(copy(data), data);
		}

		else if(getCeilingDimension() != null)
		{
			ChunkData ceiling = createChunkData(world);
			InvertedBiomeGrid ceilingGrid = new InvertedBiomeGrid(grid);
			targetCeiling();
			generate(random, x, z, ceiling, ceilingGrid);
			writeInverted(ceiling, data);
		}
	}

	private void targetFloor()
	{
		generatingCeiling = false;
	}

	private void targetCeiling()
	{
		generatingCeiling = true;
	}

	private void generate(RNG random, int x, int z, ChunkData ceiling, BiomeGrid grid)
	{
		super.onGenerate(random, x, z, ceiling, grid);
	}

	@Override
	public IrisDimension getDimension()
	{
		return generatingCeiling ? getCeilingDimension() : getFloorDimension();
	}

	public IrisDimension getFloorDimension()
	{
		return super.getDimension();
	}

	public IrisDimension getCeilingDimension()
	{
		if(getFloorDimension().getCeiling().isEmpty())
		{
			return null;
		}

		IrisDimension c = Iris.data.getDimensionLoader().load(getFloorDimension().getCeiling());

		if(c != null)
		{
			c.setInverted(true);
		}

		return c;
	}

	public void writeInverted(ChunkData data, ChunkData into)
	{
		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < data.getMaxHeight(); j++)
			{
				for(int k = 0; k < 16; k++)
				{
					BlockData b = data.getBlockData(i, j, k);

					if(b == null || b.getMaterial().equals(Material.AIR))
					{
						continue;
					}

					into.setBlock(i, data.getMaxHeight() - j, k, b);
				}
			}
		}
	}

	public ChunkData copy(ChunkData d)
	{
		ChunkData copy = createChunkData(world);

		for(int i = 0; i < 16; i++)
		{
			for(int j = 0; j < d.getMaxHeight(); j++)
			{
				for(int k = 0; k < 16; k++)
				{
					BlockData b = d.getBlockData(i, j, k);

					if(b == null || b.getMaterial().equals(Material.AIR))
					{
						continue;
					}

					copy.setBlock(i, j, k, b);
				}
			}
		}

		return copy;
	}
}
