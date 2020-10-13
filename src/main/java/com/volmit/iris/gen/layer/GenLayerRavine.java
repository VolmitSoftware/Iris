package com.volmit.iris.gen.layer;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.gen.TopographicTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicSliverMap;
import com.volmit.iris.gen.scaffold.TerrainChunk;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.util.B;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.GenLayer;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.MathHelper;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class GenLayerRavine extends GenLayer
{
	private static final BlockData CAVE_AIR = B.get("CAVE_AIR");
	private static final BlockData LAVA = B.get("LAVA");
	private CNG cng;

	public GenLayerRavine(TopographicTerrainProvider iris, RNG rng)
	{
		super(iris, rng);
		cng = NoiseStyle.IRIS_THICK.create(rng.nextParallelRNG(29596878));
	}

	@Override
	public double generate(double x, double z)
	{
		return 0;
	}

	private void set(TerrainChunk pos, int x, int y, int z, BlockData b, HeightMap h, AtomicSliverMap map)
	{
		pos.setBlock(x, y, z, b);
		map.getSliver(x, z).set(y, b);

		if(h.getHeight(x, z) > y)
		{
			h.setHeight(x, z, y);
		}
	}

	private BlockData get(TerrainChunk pos, int x, int y, int z)
	{
		return pos.getBlockData(x, y, z);
	}

	private BlockData getSurfaceBlock(TerrainChunk pos, BiomeMap map, int n6, int i, RNG rmg)
	{
		return map.getBiome(n6, i).getSurfaceBlock(n6, i, rmg, iris.getData());
	}

	private float[] ravineCache = new float[1024];

	private void doRavine(long seed, int tx, int tz, ChunkPosition pos, double sx, double sy, double sz, float f, float f2, float f3, int n3, int n4, double d4, RNG bbx, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{
		int n5;
		RNG random = new RNG(seed);
		double x = tx * 16 + 8;
		double z = tz * 16 + 8;
		float f4 = 0.0f;
		float f5 = 0.0f;
		if(n4 <= 0)
		{
			n5 = 8 * 16 - 16;
			n4 = n5 - random.nextInt(n5 / 4);
		}
		n5 = 0;
		if(n3 == -1)
		{
			n3 = n4 / 2;
			n5 = 1;
		}
		float f6 = 1.0f;
		for(int i = 0; i < 256; ++i)
		{
			if(i == 0 || random.nextInt(iris.getDimension().getRavineRibRarity()) == 0)
			{
				f6 = 1.0f + random.nextFloat() * random.nextFloat() * 1.0f;
			}
			this.ravineCache[i] = f6 * f6;
		}
		while(n3 < n4)
		{
			double d7 = 1.5 + (double) (MathHelper.sin((float) ((float) n3 * 3.1415927f / (float) n4)) * f * 1.0f);
			double d8 = d7 * d4;
			d7 *= (double) random.nextFloat() * 0.25 + 0.75;
			d8 *= (double) random.nextFloat() * 0.25 + 0.75;
			float f7 = MathHelper.cos((float) f3);
			float f8 = MathHelper.sin((float) f3);
			sx += (double) (MathHelper.cos((float) f2) * f7);
			sy += (double) f8;
			sz += (double) (MathHelper.sin((float) f2) * f7);
			f3 *= 0.7f;
			f3 += f5 * 0.05f;
			f2 += f4 * 0.05f;
			f5 *= 0.8f;
			f4 *= 0.5f;
			f5 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0f;
			f4 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0f;
			if(n5 != 0 || random.nextInt(4) != 0)
			{
				double d9 = sx - x;
				double d10 = sz - z;
				double d11 = n4 - n3;
				double d12 = f + 2.0f + 16.0f;
				if(d9 * d9 + d10 * d10 - d11 * d11 > d12 * d12)
				{
					return;
				}
				if(sx >= x - 16.0 - d7 * 2.0 && sz >= z - 16.0 - d7 * 2.0 && sx <= x + 16.0 + d7 * 2.0 && sz <= z + 16.0 + d7 * 2.0)
				{
					int n6;
					int n7 = MathHelper.floor((double) (sx - d7)) - tx * 16 - 1;
					int n8 = MathHelper.floor((double) (sx + d7)) - tx * 16 + 1;
					int n9 = MathHelper.floor((double) (sy - d8)) - 1;
					int n10 = MathHelper.floor((double) (sy + d8)) + 1;
					int n11 = MathHelper.floor((double) (sz - d7)) - tz * 16 - 1;
					int n12 = MathHelper.floor((double) (sz + d7)) - tz * 16 + 1;
					if(n7 < 0)
					{
						n7 = 0;
					}
					if(n8 > 16)
					{
						n8 = 16;
					}
					if(n9 < 1)
					{
						n9 = 1;
					}
					if(n10 > 248)
					{
						n10 = 248;
					}
					if(n11 < 0)
					{
						n11 = 0;
					}
					if(n12 > 16)
					{
						n12 = 16;
					}
					boolean bl = false;
					for(int i = n7; !bl && i < n8; ++i)
					{
						for(n6 = n11; !bl && n6 < n12; ++n6)
						{
							for(int j = n10 + 1; !bl && j >= n9 - 1; --j)
							{
								if(j < 0 || j >= 256)
								{
									continue;
								}

								BlockData bb = get(terrain, i, j, n6);

								if(B.isWater(bb))
								{
									bl = true;
								}

								if(j == n9 - 1 || i == n7 || i == n8 - 1 || n6 == n11 || n6 == n12 - 1)
								{
									continue;
								}
								j = n9;
							}
						}
					}
					if(!bl)
					{
						BlockPosition bps = new BlockPosition(0, 0, 0);
						for(n6 = n7; n6 < n8; ++n6)
						{
							double d13 = ((double) (n6 + tx * 16) + 0.5 - sx) / d7;
							for(int i = n11; i < n12; ++i)
							{
								double d14 = ((double) (i + tz * 16) + 0.5 - sz) / d7;
								boolean bl2 = false;
								if(d13 * d13 + d14 * d14 >= 1.0)
								{
									continue;
								}
								for(int j = n10; j > n9; --j)
								{
									double d15 = ((double) (j - 1) + 0.5 - sy) / d8;
									if((d13 * d13 + d14 * d14) * (double) this.ravineCache[j - 1] + d15 * d15 / 6.0 >= 1.0)
									{
										continue;
									}

									BlockData blockData = get(terrain, n6, j, i);

									if(isSurface(blockData))
									{
										bl2 = true;
									}

									if(j - 1 < 10)
									{
										set(terrain, n6, j, i, LAVA, height, map);
										continue;
									}

									set(terrain, n6, j, i, CAVE_AIR, height, map);
									if(!bl2 || !isDirt(get(terrain, n6, j - 1, i)))
									{
										continue;
									}

									cSet(bps, n6 + tx * 16, 0, i + tz * 16);
									set(terrain, n6, j - 1, i, getSurfaceBlock(terrain, biomeMap, n6, i, rng), height, map);
								}
							}
						}
						if(n5 != 0)
							break;
					}
				}
			}
			++n3;
		}
	}

	private BlockPosition cSet(BlockPosition bb, double var0, double var2, double var4)
	{
		bb.setX(MathHelper.floor((double) var0));
		bb.setY(MathHelper.floor((double) var2));
		bb.setZ(MathHelper.floor((double) var4));

		return bb;
	}

	private boolean isDirt(BlockData d)
	{
		//@builder
		Material m = d.getMaterial();
		return m.equals(Material.DIRT) || 
				m.equals(Material.COARSE_DIRT) || 
				m.equals(Material.SAND);
		//@done
	}

	private boolean isSurface(BlockData d)
	{
		//@builder
		Material m = d.getMaterial();
		return m.equals(Material.GRASS_BLOCK) || 
				m.equals(Material.DIRT) || 
				m.equals(Material.COARSE_DIRT) || 
				m.equals(Material.PODZOL) || 
				m.equals(Material.SAND);
		//@done
	}

	public void genRavines(int n, int n2, ChunkPosition chunkSnapshot, RNG bbb, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{
		RNG b = this.rng.nextParallelRNG(21949666);
		RNG bx = this.rng.nextParallelRNG(6676121);
		long l = b.nextLong();
		long l2 = b.nextLong();
		for(int i = n - 8; i <= n + 8; ++i)
		{
			for(int j = n2 - 8; j <= n2 + 8; ++j)
			{
				long l3 = (long) i * l;
				long l4 = (long) j * l2;
				bx = this.rng.nextParallelRNG((int) (l3 ^ l4 ^ 6676121));
				doRavines(i, j, n, n2, chunkSnapshot, bx, terrain, height, biomeMap, map);
			}
		}
	}

	private void doRavines(int tx, int tz, int sx, int sz, ChunkPosition chunkSnapshot, RNG b, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{
		if(b.nextInt(iris.getDimension().getRavineRarity()) != 0)
		{
			return;
		}

		double x = tx * 16 + b.nextInt(16);
		double d2 = b.nextInt(b.nextInt(40) + 8) + 20;
		double z = tz * 16 + b.nextInt(16);
		int n5 = 1;
		for(int i = 0; i < n5; ++i)
		{
			float f = b.nextFloat() * 3.1415927f * 2.0f;
			float f2 = (b.nextFloat() - 0.5f) * 2.0f / 8.0f;
			float f3 = (b.nextFloat() * 2.0f + b.nextFloat()) * 2.0f;
			this.doRavine(b.nextLong(), sx, sz, chunkSnapshot, x, d2, z, f3, f, f2, 0, 0, 3.0, b, terrain, height, biomeMap, map);
		}
	}

	public void generateRavines(RNG nextParallelRNG, int x, int z, TerrainChunk terrain, HeightMap height, BiomeMap biomeMap, AtomicSliverMap map)
	{
		genRavines(x, z, new ChunkPosition(x, z), nextParallelRNG.nextParallelRNG(x).nextParallelRNG(z), terrain, height, biomeMap, map);
	}
}
