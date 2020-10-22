package com.volmit.iris.gen.post;

import org.bukkit.Material;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Slab.Type;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.gen.PostBlockTerrainProvider;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.util.B;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.FastBlockData;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

public class PostMasterPatcher extends IrisPostBlockFilter
{
	private static final FastBlockData WATER = B.getBlockData("WATER");
	private static final FastBlockData AIR = B.getBlockData("AIR");
	private final RNG rng;

	@DontObfuscate
	public PostMasterPatcher(PostBlockTerrainProvider gen, int phase)
	{
		super(gen, phase);
		rng = gen.getMasterRandom().nextParallelRNG(1239456);
	}

	@DontObfuscate
	public PostMasterPatcher(PostBlockTerrainProvider gen)
	{
		this(gen, 0);
	}

	@Override
	public void onPost(int x, int z, int currentPostX, int currentPostZ, ChunkData currentData, KList<Runnable> q)
	{
		int h = highestTerrainOrCarvingBlock(x, z);
		int ha = highestTerrainOrCarvingBlock(x + 1, z);
		int hb = highestTerrainOrCarvingBlock(x, z + 1);
		int hc = highestTerrainOrCarvingBlock(x - 1, z);
		int hd = highestTerrainOrCarvingBlock(x, z - 1);

		// Floating Nibs
		int g = 0;

		if(h < 1)
		{
			return;
		}

		g += ha < h - 1 ? 1 : 0;
		g += hb < h - 1 ? 1 : 0;
		g += hc < h - 1 ? 1 : 0;
		g += hd < h - 1 ? 1 : 0;

		if(g == 4 && isAir(x, h - 1, z, currentPostX, currentPostZ, currentData))
		{
			setPostBlock(x, h, z, AIR, currentPostX, currentPostZ, currentData);

			for(int i = h - 1; i > 0; i--)
			{
				if(!isAir(x, i, z, currentPostX, currentPostZ, currentData))
				{
					updateHeight(x, z, i);
					h = i;
					break;
				}
			}
		}

		// Nibs
		g = 0;
		g += ha == h - 1 ? 1 : 0;
		g += hb == h - 1 ? 1 : 0;
		g += hc == h - 1 ? 1 : 0;
		g += hd == h - 1 ? 1 : 0;

		if(g >= 3)
		{
			FastBlockData bc = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);
			FastBlockData b = getPostBlock(x, h + 1, z, currentPostX, currentPostZ, currentData);
			Material m = bc.getMaterial();

			if(m.isSolid())
			{
				setPostBlock(x, h, z, b, currentPostX, currentPostZ, currentData);
				updateHeight(x, z, h - 1);
				h--;
			}
		}

		else
		{
			// Potholes
			g = 0;
			g += ha == h + 1 ? 1 : 0;
			g += hb == h + 1 ? 1 : 0;
			g += hc == h + 1 ? 1 : 0;
			g += hd == h + 1 ? 1 : 0;

			if(g >= 3)
			{
				FastBlockData ba = getPostBlock(x, ha, z, currentPostX, currentPostZ, currentData);
				FastBlockData bb = getPostBlock(x, hb, z, currentPostX, currentPostZ, currentData);
				FastBlockData bc = getPostBlock(x, hc, z, currentPostX, currentPostZ, currentData);
				FastBlockData bd = getPostBlock(x, hd, z, currentPostX, currentPostZ, currentData);
				g = 0;
				g = B.isSolid(ba) ? g + 1 : g;
				g = B.isSolid(bb) ? g + 1 : g;
				g = B.isSolid(bc) ? g + 1 : g;
				g = B.isSolid(bd) ? g + 1 : g;

				if(g >= 3)
				{
					setPostBlock(x, h + 1, z, getPostBlock(x, h, z, currentPostX, currentPostZ, currentData), currentPostX, currentPostZ, currentData);
					updateHeight(x, z, h + 1);
					h++;
				}
			}
		}

		// Wall Patcher
		IrisBiome biome = gen.sampleTrueBiome(x, z);

		if(gen.getDimension().isPostProcessingWalls())
		{
			if(!biome.getWall().getPalette().isEmpty())
			{
				if(ha < h - 2 || hb < h - 2 || hc < h - 2 || hd < h - 2)
				{
					boolean brokeGround = false;
					int max = Math.abs(Math.max(h - ha, Math.max(h - hb, Math.max(h - hc, h - hd))));

					for(int i = h; i > h - max; i--)
					{
						FastBlockData d = biome.getWall().get(rng, x + i, i + h, z + i, gen.getData());

						if(d != null)
						{
							if(isAirOrWater(x, i, z, currentPostX, currentPostZ, currentData))
							{
								if(brokeGround)
								{
									break;
								}

								continue;
							}

							setPostBlock(x, i, z, d, currentPostX, currentPostZ, currentData);
							brokeGround = true;
						}
					}
				}
			}
		}

		// Slab
		if(gen.getDimension().isPostProcessingSlabs())
		{
			//@builder
			if((ha == h + 1 && isSolidNonSlab(x + 1, ha, z, currentPostX, currentPostZ, currentData)) 
			|| (hb == h + 1 && isSolidNonSlab(x, hb, z + 1, currentPostX, currentPostZ, currentData)) 
			|| (hc == h + 1 && isSolidNonSlab(x - 1, hc, z, currentPostX, currentPostZ, currentData)) 
			|| (hd == h + 1 && isSolidNonSlab(x, hd, z - 1, currentPostX, currentPostZ, currentData)))
			//@done
			{
				FastBlockData d = biome.getSlab().get(rng, x, h, z, gen.getData());

				if(d != null)
				{
					boolean cancel = false;

					if(B.isAir(d))
					{
						cancel = true;
					}

					if(d.getMaterial().equals(Material.SNOW) && h + 1 <= gen.getFluidHeight())
					{
						cancel = true;
					}

					if(isSnowLayer(x, h, z, currentPostX, currentPostZ, currentData))
					{
						cancel = true;
					}

					if(!cancel && isAirOrWater(x, h + 1, z, currentPostX, currentPostZ, currentData))
					{
						setPostBlock(x, h + 1, z, d, currentPostX, currentPostZ, currentData);
						updateHeight(x, z, h + 1);
						h++;
					}
				}
			}
		}

		// Waterlogging
		FastBlockData b = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);

		if(b.getBlockData() instanceof Waterlogged)
		{
			Waterlogged ww = (Waterlogged) b.getBlockData();
			boolean w = false;
			if(isWaterOrWaterlogged(x, h + 1, z, currentPostX, currentPostZ, currentData))
			{
				w = true;
			}

			else if((isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData)))
			{
				w = true;
			}

			if(w != ww.isWaterlogged())
			{
				ww.setWaterlogged(w);
				setPostBlock(x, h, z, b, currentPostX, currentPostZ, currentData);
			}
		}

		else if(b.getMaterial().equals(Material.AIR) && h <= gen.getFluidHeight())
		{
			if((isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData)))
			{
				setPostBlock(x, h, z, WATER, currentPostX, currentPostZ, currentData);
			}
		}

		// Foliage
		b = getPostBlock(x, h + 1, z, currentPostX, currentPostZ, currentData);

		if(B.isFoliage(b) || b.getMaterial().equals(Material.DEAD_BUSH))
		{
			Material onto = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData).getMaterial();

			if(!B.canPlaceOnto(b.getMaterial(), onto))
			{
				setPostBlock(x, h + 1, z, AIR, currentPostX, currentPostZ, currentData);
			}
		}

		if(gen.getDimension().isPostProcessCaves())
		{
			IrisBiome cave = gen.sampleTrueBiome(x, 1, z);

			if(cave.getInferredType().equals(InferredType.CAVE))
			{
				for(CaveResult i : gen.getCaves(x, z))
				{
					if(i.getCeiling() > 256 || i.getFloor() < 0)
					{
						continue;
					}

					int f = i.getFloor();
					int fa = nearestCaveFloor(f, x + 1, z, currentPostX, currentPostZ, currentData);
					int fb = nearestCaveFloor(f, x, z + 1, currentPostX, currentPostZ, currentData);
					int fc = nearestCaveFloor(f, x - 1, z, currentPostX, currentPostZ, currentData);
					int fd = nearestCaveFloor(f, x, z - 1, currentPostX, currentPostZ, currentData);
					int c = i.getCeiling();
					int ca = nearestCaveCeiling(c, x + 1, z, currentPostX, currentPostZ, currentData);
					int cb = nearestCaveCeiling(c, x, z + 1, currentPostX, currentPostZ, currentData);
					int cc = nearestCaveCeiling(c, x - 1, z, currentPostX, currentPostZ, currentData);
					int cd = nearestCaveCeiling(c, x, z - 1, currentPostX, currentPostZ, currentData);

					// Cave Nibs
					g = 0;
					g += fa == f - 1 ? 1 : 0;
					g += fb == f - 1 ? 1 : 0;
					g += fc == f - 1 ? 1 : 0;
					g += fd == f - 1 ? 1 : 0;

					if(g >= 3)
					{
						FastBlockData bc = getPostBlock(x, f, z, currentPostX, currentPostZ, currentData);
						b = getPostBlock(x, f + 1, z, currentPostX, currentPostZ, currentData);
						Material m = bc.getMaterial();

						if(m.isSolid())
						{
							setPostBlock(x, f, z, b, currentPostX, currentPostZ, currentData);
							updateHeight(x, z, f - 1);
							h--;
						}
					}

					else
					{
						// Cave Potholes
						g = 0;
						g += fa == f + 1 ? 1 : 0;
						g += fb == f + 1 ? 1 : 0;
						g += fc == f + 1 ? 1 : 0;
						g += fd == f + 1 ? 1 : 0;

						if(g >= 3)
						{
							FastBlockData ba = getPostBlock(x, fa, z, currentPostX, currentPostZ, currentData);
							FastBlockData bb = getPostBlock(x, fb, z, currentPostX, currentPostZ, currentData);
							FastBlockData bc = getPostBlock(x, fc, z, currentPostX, currentPostZ, currentData);
							FastBlockData bd = getPostBlock(x, fd, z, currentPostX, currentPostZ, currentData);
							g = 0;
							g = B.isSolid(ba) ? g + 1 : g;
							g = B.isSolid(bb) ? g + 1 : g;
							g = B.isSolid(bc) ? g + 1 : g;
							g = B.isSolid(bd) ? g + 1 : g;

							if(g >= 3)
							{
								setPostBlock(x, f + 1, z, getPostBlock(x, f, z, currentPostX, currentPostZ, currentData), currentPostX, currentPostZ, currentData);
								updateHeight(x, z, f + 1);
								h++;
							}
						}
					}

					if(gen.getDimension().isPostProcessingSlabs())
					{
						//@builder
						if((fa == f + 1 && isSolidNonSlab(x + 1, fa, z, currentPostX, currentPostZ, currentData)) 
						|| (fb == f + 1 && isSolidNonSlab(x, fb, z + 1, currentPostX, currentPostZ, currentData)) 
						|| (fc == f + 1 && isSolidNonSlab(x - 1, fc, z, currentPostX, currentPostZ, currentData)) 
						|| (fd == f + 1 && isSolidNonSlab(x, fd, z - 1, currentPostX, currentPostZ, currentData)))
						//@done
						{
							FastBlockData d = cave.getSlab().get(rng, x, f, z, gen.getData());

							if(d != null)
							{
								boolean cancel = false;

								if(B.isAir(d))
								{
									cancel = true;
								}

								if(d.getMaterial().equals(Material.SNOW) && f + 1 <= gen.getFluidHeight())
								{
									cancel = true;
								}

								if(isSnowLayer(x, f, z, currentPostX, currentPostZ, currentData))
								{
									cancel = true;
								}

								if(!cancel && isAirOrWater(x, f + 1, z, currentPostX, currentPostZ, currentData))
								{
									setPostBlock(x, f + 1, z, d, currentPostX, currentPostZ, currentData);
								}
							}
						}

						//@builder
						if((ca == c - 1 && isSolidNonSlab(x + 1, ca, z, currentPostX, currentPostZ, currentData)) 
						|| (cb == c - 1 && isSolidNonSlab(x, cb, z + 1, currentPostX, currentPostZ, currentData)) 
						|| (cc == c - 1 && isSolidNonSlab(x - 1, cc, z, currentPostX, currentPostZ, currentData)) 
						|| (cd == c - 1 && isSolidNonSlab(x, cd, z - 1, currentPostX, currentPostZ, currentData)))
						//@done
						{
							FastBlockData d = cave.getSlab().get(rng, x, c, z, gen.getData());

							if(d != null)
							{
								boolean cancel = false;

								if(B.isAir(d))
								{
									cancel = true;
								}

								if(!(d.getBlockData() instanceof Slab))
								{
									cancel = true;
								}

								if(isSnowLayer(x, c, z, currentPostX, currentPostZ, currentData))
								{
									cancel = true;
								}

								if(!cancel && isAirOrWater(x, c, z, currentPostX, currentPostZ, currentData))
								{
									Slab slab = (Slab) d.getBlockData().clone();
									slab.setType(Type.TOP);
									setPostBlock(x, c, z, d, currentPostX, currentPostZ, currentData);
								}
							}
						}
					}
				}
			}
		}
	}

	private int nearestCaveFloor(int floor, int x, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		if(floor > 255)
		{
			return 255;
		}

		if(B.isAir(getPostBlock(x, floor, z, currentPostX, currentPostZ, currentData)))
		{
			if(B.isAir(getPostBlock(x, floor - 1, z, currentPostX, currentPostZ, currentData)))
			{
				return floor - 2;
			}

			return floor - 1;
		}

		else
		{
			if(!B.isAir(getPostBlock(x, floor + 1, z, currentPostX, currentPostZ, currentData)))
			{
				if(!B.isAir(getPostBlock(x, floor + 2, z, currentPostX, currentPostZ, currentData)))
				{
					return floor + 2;
				}

				return floor + 1;
			}

			return floor;
		}
	}

	private int nearestCaveCeiling(int ceiling, int x, int z, int currentPostX, int currentPostZ, ChunkData currentData)
	{
		if(ceiling > 255)
		{
			return 255;
		}

		if(B.isAir(getPostBlock(x, ceiling, z, currentPostX, currentPostZ, currentData)))
		{
			if(B.isAir(getPostBlock(x, ceiling + 1, z, currentPostX, currentPostZ, currentData)))
			{
				return ceiling + 2;
			}

			return ceiling + 1;
		}

		else
		{
			if(!B.isAir(getPostBlock(x, ceiling - 1, z, currentPostX, currentPostZ, currentData)))
			{
				if(!B.isAir(getPostBlock(x, ceiling - 2, z, currentPostX, currentPostZ, currentData)))
				{
					return ceiling - 2;
				}

				return ceiling - 1;
			}

			return ceiling;
		}
	}
}
