package ninja.bytecode.iris.generator;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.World;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.layer.GenLayerBiome;
import ninja.bytecode.iris.object.IrisBiome;
import ninja.bytecode.iris.object.IrisBiomeGeneratorLink;
import ninja.bytecode.iris.object.IrisGenerator;
import ninja.bytecode.iris.object.IrisRegion;
import ninja.bytecode.iris.util.BiomeResult;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.ChronoLatch;
import ninja.bytecode.iris.util.ChunkPosition;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;
import ninja.bytecode.shuriken.collections.KMap;
import ninja.bytecode.shuriken.math.M;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class BiomeChunkGenerator extends DimensionChunkGenerator
{
	protected ReentrantLock regLock;
	protected KMap<String, IrisGenerator> generators;
	protected GenLayerBiome glBiome;
	protected CNG masterFracture;
	protected KMap<ChunkPosition, BiomeResult> biomeHitCache;
	protected ChronoLatch cwarn = new ChronoLatch(1000);

	public BiomeChunkGenerator(String dimensionName)
	{
		super(dimensionName);
		generators = new KMap<>();
		regLock = new ReentrantLock();
		biomeHitCache = new KMap<>();
	}

	public void onInit(World world, RNG rng)
	{
		loadGenerators();
		glBiome = new GenLayerBiome(this, masterRandom.nextParallelRNG(1));
		masterFracture = CNG.signature(rng.nextParallelRNG(13)).scale(0.12);
	}

	public void onHotloaded()
	{
		biomeHitCache = new KMap<>();
		generators.clear();
		loadGenerators();
	}

	public void registerGenerator(IrisGenerator g)
	{
		regLock.lock();
		if(g.getLoadKey() == null || generators.containsKey(g.getLoadKey()))
		{
			regLock.unlock();
			return;
		}

		regLock.unlock();
		generators.put(g.getLoadKey(), g);
	}

	protected double getBiomeHeight(double rx, double rz)
	{
		double h = 0;

		for(IrisGenerator i : generators.values())
		{
			h += interpolateGenerator(rx, rz, i);
		}

		return h;
	}

	protected double interpolateGenerator(double rx, double rz, IrisGenerator gen)
	{
		double hi = IrisInterpolation.getNoise(gen.getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), gen.getInterpolationScale(), (xx, zz) ->
		{
			IrisBiome b = sampleBiome((int) xx, (int) zz).getBiome();

			for(IrisBiomeGeneratorLink i : b.getGenerators())
			{
				if(i.getGenerator().equals(gen.getLoadKey()))
				{
					return i.getMax();
				}
			}

			return 0;
		});

		double lo = IrisInterpolation.getNoise(gen.getInterpolationFunction(), (int) Math.round(rx), (int) Math.round(rz), gen.getInterpolationScale(), (xx, zz) ->
		{
			IrisBiome b = sampleBiome((int) xx, (int) zz).getBiome();

			for(IrisBiomeGeneratorLink i : b.getGenerators())
			{
				if(i.getGenerator().equals(gen.getLoadKey()))
				{
					return i.getMin();
				}
			}

			return 0;
		});

		return M.lerp(lo, hi, gen.getHeight(rx, rz, world.getSeed() + 239945));
	}

	protected void loadGenerators()
	{
		KList<String> touch = new KList<>();
		KList<String> loadQueue = new KList<>();

		for(String i : getDimension().getRegions())
		{
			IrisRegion r = Iris.data.getRegionLoader().load(i);

			if(r != null)
			{
				loadQueue.addAll(r.getLandBiomes());
				loadQueue.addAll(r.getSeaBiomes());
				loadQueue.addAll(r.getShoreBiomes());
			}
		}

		while(!loadQueue.isEmpty())
		{
			String next = loadQueue.pop();

			if(!touch.contains(next))
			{
				touch.add(next);
				IrisBiome biome = Iris.data.getBiomeLoader().load(next);
				biome.getGenerators().forEach((i) -> registerGenerator(i.getCachedGenerator()));
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
		ChunkPosition pos = new ChunkPosition(x, z);

		if(biomeHitCache.containsKey(pos))
		{
			return biomeHitCache.get(pos);
		}

		double wx = getModifiedX(x, z);
		double wz = getModifiedZ(x, z);
		IrisRegion region = glBiome.getRegion(wx, wz);
		BiomeResult res = glBiome.generateRegionData(wx, wz, region);
		biomeHitCache.put(pos, res);

		return res;
	}
}
