package com.volmit.iris.gen.v2;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.layer.GenLayerBiome;
import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.gen.v2.scaffold.stream.Interpolated;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisBiomePaletteLayer;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisGenerator;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.M;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Data
public class IrisComplex implements DataProvider
{
	private RNG rng;
	private IrisDataManager data;
	private KList<IrisGenerator> generators;
	private static final BlockData AIR = Material.AIR.createBlockData();
	private ProceduralStream<IrisRegion> regionStream;
	private ProceduralStream<InferredType> bridgeStream;
	private ProceduralStream<IrisBiome> landBiomeStream;
	private ProceduralStream<IrisBiome> caveBiomeStream;
	private ProceduralStream<IrisBiome> seaBiomeStream;
	private ProceduralStream<IrisBiome> shoreBiomeStream;
	private ProceduralStream<IrisBiome> baseBiomeStream;
	private ProceduralStream<IrisBiome> trueBiomeStream;
	private ProceduralStream<Double> heightStream;
	private ProceduralStream<BlockData> terrainStream;
	private ProceduralStream<BlockData> rockStream;
	private ProceduralStream<BlockData> fluidStream;

	public IrisComplex()
	{

	}

	public ProceduralStream<IrisBiome> getBiomeStream(InferredType type)
	{
		switch(type)
		{
			case CAVE:
				return caveBiomeStream;
			case DEFER:
				break;
			case LAKE:
				break;
			case LAND:
				return landBiomeStream;
			case RIVER:
				break;
			case SEA:
				return seaBiomeStream;
			case SHORE:
				return shoreBiomeStream;
			default:
				break;
		}

		return null;
	}

	public void flash(long seed, IrisDimension dimension, IrisDataManager data)
	{
		this.rng = new RNG(seed);
		this.data = data;
		double fluidHeight = dimension.getFluidHeight();
		generators = new KList<>();
		RNG rng = new RNG(seed);
		//@builder
		dimension.getRegions().forEach((i) -> data.getRegionLoader().load(i)
			.getAllBiomes(this).forEach((b) -> b
				.getGenerators()
				.forEach((c) -> registerGenerator(c.getCachedGenerator(this)))));
		rockStream = dimension.getRockPalette().getLayerGenerator(rng.nextRNG(), data).stream()
			.select(dimension.getRockPalette().getBlockData(data))
			.convert((v) -> v.getBlockData());
		fluidStream = dimension.getFluidPalette().getLayerGenerator(rng.nextRNG(), data).stream()
			.select(dimension.getFluidPalette().getBlockData(data))
			.convert((v) -> v.getBlockData());
		regionStream = dimension.getRegionStyle().create(rng.nextRNG()).stream()
			.zoom(dimension.getRegionZoom())
			.select(dimension.getRegions())
			.convertCached((s) -> data.getRegionLoader().load(s))
			.cache2D(1024);
		caveBiomeStream = regionStream.convertCached((r) 
			-> dimension.getCaveBiomeStyle().create(rng.nextRNG()).stream()
				.zoom(r.getCaveBiomeZoom())
				.selectRarity(r.getCaveBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s))
			).convertAware2D((str, x, z) -> str.get(x, z))
				.cache2D(1024);
		landBiomeStream = regionStream.convertCached((r) 
			-> dimension.getLandBiomeStyle().create(rng.nextRNG()).stream()
				.zoom(r.getLandBiomeZoom())
				.selectRarity(r.getLandBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s))
			).convertAware2D((str, x, z) -> str.get(x, z))
				.cache2D(1024);
		seaBiomeStream = regionStream.convertCached((r) 
			-> dimension.getSeaBiomeStyle().create(rng.nextRNG()).stream()
				.zoom(r.getSeaBiomeZoom())
				.selectRarity(r.getSeaBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s))
			).convertAware2D((str, x, z) -> str.get(x, z))
				.cache2D(1024);
		shoreBiomeStream = regionStream.convertCached((r) 
			-> dimension.getShoreBiomeStyle().create(rng.nextRNG()).stream()
				.zoom(r.getShoreBiomeZoom())
				.selectRarity(r.getShoreBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s))
			).convertAware2D((str, x, z) -> str.get(x, z))
				.cache2D(1024);
		bridgeStream = dimension.getContinentalStyle().create(rng.nextRNG()).stream()
			.convert((v) -> v >= dimension.getLandChance() ? InferredType.SEA : InferredType.LAND);
		baseBiomeStream = bridgeStream.convertAware2D((t, x, z) -> t.equals(InferredType.SEA) 
			? seaBiomeStream.get(x, z) : landBiomeStream.get(x, z))
			.convertAware2D(this::implode)
			.cache2D(1024);
		heightStream = baseBiomeStream.convertAware2D((b, x, z) -> getHeight(b, x, z, seed))
			.forceDouble().add(fluidHeight).roundDouble()
			.cache2D(1024);
		trueBiomeStream = heightStream
				.convertAware2D((h, x, z) -> 
					fixBiomeType(h, baseBiomeStream.get(x, z),regionStream.get(x, z), x, z, fluidHeight))
				.cache2D(1024);
		terrainStream = ProceduralStream.of((x, y, z) -> {
			double height = heightStream.get(x, z);
			IrisBiome biome = trueBiomeStream.get(x, z);
			int depth = (int) (Math.round(height) - y);
			int atDepth = 0;
			
			if(depth < -1)
			{
				return AIR;
			}
			
			for(IrisBiomePaletteLayer i : biome.getLayers())
			{
				int th = i.getHeightGenerator(rng, data).fit(i.getMinHeight(), i.getMaxHeight(), x, z);
				
				if(atDepth + th >= depth)
				{
					return i.get(rng, x, y, z, data).getBlockData();
				}
				
				atDepth += th;
			}
			
			return rockStream.get(x, y, z);
		}, new Interpolated<BlockData>()
		{
			@Override
			public double toDouble(BlockData t)
			{
				return 0;
			}

			@Override
			public BlockData fromDouble(double d)
			{
				return null;
			}
		});
		//@done
	}

	private IrisBiome implode(IrisBiome b, Double x, Double z)
	{
		if(b.getChildren().isEmpty())
		{
			return b;
		}

		return implode(b, x, z, 3);
	}

	private IrisBiome implode(IrisBiome b, Double x, Double z, int max)
	{
		if(max < 0)
		{
			return b;
		}

		if(b.getChildren().isEmpty())
		{
			return b;
		}

		CNG childCell = b.getChildrenGenerator(rng, 123, b.getChildShrinkFactor());
		KList<IrisBiome> chx = b.getRealChildren(this).copy();
		chx.add(b);
		IrisBiome biome = childCell.fitRarity(chx, x, z);
		biome.setInferredType(b.getInferredType());
		return implode(biome, x, z, max - 1);
	}

	private IrisBiome fixBiomeType(Double height, IrisBiome biome, IrisRegion region, Double x, Double z, double fluidHeight)
	{
		double sh = region.getShoreHeight(x, z);

		if(height > fluidHeight && height <= fluidHeight + sh && !biome.isShore())
		{
			return shoreBiomeStream.get(x, z);
		}

		if(height > fluidHeight + sh && !biome.isLand())
		{
			return landBiomeStream.get(x, z);
		}

		if(height <= fluidHeight && !biome.isAquatic())
		{
			return seaBiomeStream.get(x, z);
		}

		return biome;
	}

	private double getHeight(IrisBiome b, double x, double z, long seed)
	{
		double h = 0;

		for(IrisGenerator gen : generators)
		{
			double hi = gen.getInterpolator().interpolate(x, z, (xx, zz) ->
			{
				try
				{
					IrisBiome bx = baseBiomeStream.get(xx, zz);

					return bx.getGenLinkMax(gen.getLoadKey());
				}

				catch(Throwable e)
				{
					Iris.warn("Failed to sample hi biome at " + xx + " " + zz + " using the generator " + gen.getLoadKey());
				}

				return 0;
			});

			double lo = gen.getInterpolator().interpolate(x, z, (xx, zz) ->
			{
				try
				{
					IrisBiome bx = baseBiomeStream.get(xx, zz);

					return bx.getGenLinkMin(gen.getLoadKey());
				}

				catch(Throwable e)
				{
					Iris.warn("Failed to sample lo biome at " + xx + " " + zz + " using the generator " + gen.getLoadKey());
				}

				return 0;
			});

			h += M.lerp(lo, hi, gen.getHeight(x, z, seed + 239945));
		}

		return h;
	}

	private void registerGenerator(IrisGenerator cachedGenerator)
	{
		for(IrisGenerator i : generators)
		{
			if(i.getLoadKey().equals(cachedGenerator.getLoadKey()))
			{
				return;
			}
		}

		generators.add(cachedGenerator);
	}
}
