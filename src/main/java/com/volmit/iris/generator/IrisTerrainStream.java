package com.volmit.iris.generator;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.generator.atomics.HeightHunk;
import com.volmit.iris.generator.atomics.TerrainHunk;
import com.volmit.iris.generator.scaffold.TerrainStream;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.util.RNG;

public class IrisTerrainStream implements TerrainStream
{
	private CNG cng = NoiseStyle.IRIS_DOUBLE.create(new RNG(1234)).scale(0.5);
	private static final BlockData STONE = Material.STONE.createBlockData();

	@Override
	public long getSeed()
	{
		return 0;
	}

	@Override
	public int getHeight()
	{
		return 64;
	}

	@Override
	public int getNoise(int x, int z)
	{
		return (int) Math.round(cng.fitDouble(0, getHeight() - 1, (double) x, (double) z));
	}

	@Override
	public HeightHunk genNoise(int x1, int z1, int x2, int z2)
	{
		HeightHunk b = new HeightHunk(x2 - x1, z2 - z1);

		for(int i = 0; i < b.getW(); i++)
		{
			for(int j = 0; j < b.getD(); j++)
			{
				b.setHeight(i, getNoise(i + x1, j + z1), j);
			}
		}

		return b;
	}

	@Override
	public TerrainHunk genCarving(int x1, int z1, int x2, int z2, HeightHunk noise)
	{
		TerrainHunk t = new TerrainHunk(noise.getW(), getHeight(), noise.getD(), noise);

		for(int i = 0; i < t.getW(); i++)
		{
			for(int k = 0; k < t.getD(); k++)
			{
				int height = t.getHeight().getHeight(i, k);

				for(int j = 0; j <= height; j++)
				{
					t.setBlock(i, j, k, STONE);
				}
			}
		}

		return t;
	}

	@Override
	public TerrainHunk genTerrain(int x1, int z1, int x2, int z2, TerrainHunk t)
	{
		boolean hard = false;
		int lastHard = 255;

		for(int i = 0; i < t.getW(); i++)
		{
			for(int k = 0; k < t.getW(); k++)
			{
				int height = t.getHeight().getHeight(i, k);

				for(int j = height; j >= 0; j--)
				{
					boolean _hard = !t.getBlockData(i, j, k).getMaterial().equals(Material.VOID_AIR);

					if(!hard && _hard)
					{
						lastHard = j;
						hard = true;
					}

					else if(hard && (!_hard || j == 0))
					{
						generateSurface(x1, z1, i, lastHard, k, lastHard - j, t);
						hard = false;
					}
				}
			}
		}

		return t;
	}

	protected void generateSurface(int ox, int oz, int x, int y, int z, int depth, TerrainHunk t)
	{
		for(int i = y; i <= (y + depth); i++)
		{
			if(i == y)
			{
				t.setBlock(x, i, z, Material.GRASS_BLOCK.createBlockData());
			}
		}
	}

	@Override
	public TerrainHunk genDecorations(int x1, int z1, int x2, int z2, TerrainHunk hunk)
	{
		// TODO Auto-generated method stub
		return hunk;
	}

	@Override
	public TerrainHunk genParallax(int x1, int z1, int x2, int z2, TerrainHunk hunk)
	{
		// TODO Auto-generated method stub
		return hunk;
	}
}
