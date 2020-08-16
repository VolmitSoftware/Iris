package com.volmit.iris.gen;

import org.bukkit.World;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.layer.GenLayerBiome;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomeGeneratorLink;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.BiomeResult;
import com.volmit.iris.util.ChronoLatch;
import com.volmit.iris.util.IrisInterpolation;
import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KMap;
import com.volmit.iris.util.M;
import com.volmit.iris.util.RNG;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class BiomeChunkGenerator extends DimensionChunkGenerator
{
	protected IrisLock regLock;
	private KMap<String, IrisGenerator> generators;
	private KMap<String, IrisGenerator> ceilingGenerators;
	protected GenLayerBiome glBiome;
	protected CNG masterFracture;
	protected ChronoLatch cwarn = new ChronoLatch(1000);

	public BiomeChunkGenerator(String dimensionName)
	{
		super(dimensionName);
		generators = new KMap<>();
		ceilingGenerators = new KMap<>();
		regLock = new IrisLock("BiomeChunkGenerator");
	}

	public void onInit(World world, RNG rng)
	{
		super.onInit(world, rng);
		loadGenerators();
		glBiome = new GenLayerBiome(this, masterRandom.nextParallelRNG(1));
		masterFracture = CNG.signature(rng.nextParallelRNG(13)).scale(0.12);
	}

	@Override
	public void onHotload()
	{
		super.onHotload();
		loadGenerators();
		glBiome = new GenLayerBiome(this, masterRandom.nextParallelRNG(1));
	}

	public void registerGenerator(IrisGenerator g, IrisDimension dim)
	{
		KMap<String, IrisGenerator> generators = dim.isInverted() ? ceilingGenerators : this.generators;

		regLock.lock();
		if(g.getLoadKey() == null || generators.containsKey(g.getLoadKey()))
		{
			regLock.unlock();
			return;
		}

		regLock.unlock();
		generators.put(g.getLoadKey(), g);
	}

	protected KMap<String, IrisGenerator> getGenerators()
	{
		return getDimension().isInverted() ? ceilingGenerators : generators;
	}

	protected double getBiomeHeight(double rx, double rz, int x, int z)
	{
		double h = 0;

		for(IrisGenerator i : getGenerators().values())
		{
			h += interpolateGenerator(rx, rz, i);
		}

		return h;
	}

	protected double interpolateGenerator(double rx, double rz, IrisGenerator gen)
	{
		double hi = IrisInterpolation.getNoise(gen.getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), gen.getInterpolationScale(), (xx, zz) ->
		{
			try
			{
				IrisBiome b = sampleBiome((int) xx, (int) zz).getBiome();

				for(IrisBiomeGeneratorLink i : b.getGenerators())
				{
					if(i.getGenerator().equals(gen.getLoadKey()))
					{
						return i.getMax();
					}
				}

			}

			catch(Throwable e)
			{
				e.printStackTrace();
				Iris.warn("Failed to sample hi biome at " + rx + " " + rz + " using the generator " + gen.getLoadKey());
			}
			return 0;
		});

		double lo = IrisInterpolation.getNoise(gen.getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), gen.getInterpolationScale(), (xx, zz) ->
		{
			try
			{
				IrisBiome b = sampleBiome((int) xx, (int) zz).getBiome();

				for(IrisBiomeGeneratorLink i : b.getGenerators())
				{
					if(i.getGenerator().equals(gen.getLoadKey()))
					{
						return i.getMin();
					}
				}
			}

			catch(Throwable e)
			{
				e.printStackTrace();
				Iris.warn("Failed to sample lo biome at " + rx + " " + rz + " using the generator " + gen.getLoadKey());
			}

			return 0;
		});

		return M.lerp(lo, hi, gen.getHeight(rx, rz, world.getSeed() + 239945));
	}

	protected void loadGenerators()
	{
		generators.clear();
		ceilingGenerators.clear();
		loadGenerators(((CeilingChunkGenerator) this).getFloorDimension());
		loadGenerators(((CeilingChunkGenerator) this).getCeilingDimension());
	}

	protected void loadGenerators(IrisDimension dim)
	{
		if(dim == null)
		{
			return;
		}

		KList<String> touch = new KList<>();
		KList<String> loadQueue = new KList<>();

		for(String i : dim.getRegions())
		{
			IrisRegion r = loadRegion(i);

			if(r != null)
			{
				loadQueue.addAll(r.getLandBiomes());
				loadQueue.addAll(r.getSeaBiomes());
				loadQueue.addAll(r.getShoreBiomes());
				loadQueue.addAll(r.getRidgeBiomeKeys());
				loadQueue.addAll(r.getSpotBiomeKeys());
			}
		}

		while(!loadQueue.isEmpty())
		{
			String next = loadQueue.pop();

			if(!touch.contains(next))
			{
				touch.add(next);
				IrisBiome biome = loadBiome(next);
				biome.getGenerators().forEach((i) -> registerGenerator(i.getCachedGenerator(this), dim));
				loadQueue.addAll(biome.getChildren());
			}
		}
	}

	public IrisRegion sampleRegion(int x, int z)
	{

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		return glBiome.getRegion(wx, wz);
	}

	public BiomeResult sampleBiome(int x, int z)
	{
		return getCache().getRawBiome(x, z, () ->
		{
			if(!getDimension().getFocus().equals(""))
			{
				IrisBiome biome = loadBiome(getDimension().getFocus());

				for(String i : getDimension().getRegions())
				{
					IrisRegion reg = loadRegion(i);

					if(reg.getLandBiomes().contains(biome.getLoadKey()))
					{
						biome.setInferredType(InferredType.LAND);
						break;
					}

					if(reg.getSeaBiomes().contains(biome.getLoadKey()))
					{
						biome.setInferredType(InferredType.SEA);
						break;
					}

					if(reg.getShoreBiomes().contains(biome.getLoadKey()))
					{
						biome.setInferredType(InferredType.SHORE);
						break;
					}
				}

				return new BiomeResult(biome, 0);
			}

			double wx = getModifiedX(x, z);
			double wz = getModifiedZ(x, z);
			IrisRegion region = glBiome.getRegion(wx, wz);
			BiomeResult res = glBiome.generateRegionData(wx, wz, x, z, region);

			return res;
		});
	}
}
