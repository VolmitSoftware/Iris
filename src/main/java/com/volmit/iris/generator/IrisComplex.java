package com.volmit.iris.generator;

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.IrisSettings;
import com.volmit.iris.generator.actuator.IrisTerrainActuator;
import com.volmit.iris.generator.modifier.IrisCaveModifier;
import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.*;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.stream.ProceduralStream;
import com.volmit.iris.scaffold.stream.interpolation.Interpolated;
import com.volmit.iris.util.CaveResult;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.M;
import com.volmit.iris.util.RNG;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

@Data
public class IrisComplex implements DataProvider
{
	private RNG rng;
	private double fluidHeight;
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
	private ProceduralStream<Biome> trueBiomeDerivativeStream;
	private ProceduralStream<Double> heightStream;
	private ProceduralStream<Double> objectChanceStream;
	private ProceduralStream<Double> maxHeightStream;
	private ProceduralStream<Double> overlayStream;
	private ProceduralStream<Double> heightFluidStream;
	private ProceduralStream<Integer> trueHeightStream;
	private ProceduralStream<Double> slopeStream;
	private ProceduralStream<RNG> rngStream;
	private ProceduralStream<RNG> chunkRngStream;
	private ProceduralStream<IrisDecorator> terrainSurfaceDecoration;
	private ProceduralStream<IrisDecorator> terrainCeilingDecoration;
	private ProceduralStream<IrisDecorator> terrainCaveSurfaceDecoration;
	private ProceduralStream<IrisDecorator> terrainCaveCeilingDecoration;
	private ProceduralStream<IrisDecorator> seaSurfaceDecoration;
	private ProceduralStream<IrisDecorator> shoreSurfaceDecoration;
	private ProceduralStream<BlockData> rockStream;
	private ProceduralStream<BlockData> fluidStream;

	public ProceduralStream<IrisBiome> getBiomeStream(InferredType type)
	{
		switch(type)
		{
			case CAVE:
				return caveBiomeStream;
			case LAND:
				return landBiomeStream;
			case SEA:
				return seaBiomeStream;
			case SHORE:
				return shoreBiomeStream;
			case DEFER:
			case LAKE:
			case RIVER:
			default:
				break;
		}

		return null;
	}

	public IrisComplex(Engine engine)
	{
		int cacheSize = IrisSettings.get().getCache().getStreamingCacheSize();
		this.rng = new RNG(engine.getWorld().getSeed());
		this.data = engine.getData();
		double height = engine.getHeight();
		fluidHeight = engine.getDimension().getFluidHeight();
		generators = new KList<>();
		RNG rng = new RNG(engine.getWorld().getSeed());
		//@builder
		engine.getDimension().getRegions().forEach((i) -> data.getRegionLoader().load(i)
			.getAllBiomes(this).forEach((b) -> b
				.getGenerators()
				.forEach((c) -> registerGenerator(c.getCachedGenerator(this)))));
		overlayStream = ProceduralStream.ofDouble((x, z) -> 0D);
		engine.getDimension().getOverlayNoise().forEach((i) -> overlayStream.add((x, z) -> i.get(rng, x, z)));
		rngStream = ProceduralStream.of((x, z) -> new RNG(((x.longValue()) << 32) | (z.longValue() & 0xffffffffL))
				.nextParallelRNG(engine.getWorld().getSeed()), Interpolated.RNG);
		chunkRngStream = rngStream.blockToChunkCoords();
		rockStream = engine.getDimension().getRockPalette().getLayerGenerator(rng.nextParallelRNG(45), data).stream()
			.select(engine.getDimension().getRockPalette().getBlockData(data));
		fluidStream = engine.getDimension().getFluidPalette().getLayerGenerator(rng.nextParallelRNG(78), data).stream()
			.select(engine.getDimension().getFluidPalette().getBlockData(data));
		regionStream = engine.getDimension().getRegionStyle().create(rng.nextParallelRNG(883)).stream()
			.zoom(engine.getDimension().getRegionZoom())
			.selectRarity(engine.getDimension().getRegions())
			.convertCached((s) -> data.getRegionLoader().load(s)).cache2D(cacheSize);
		caveBiomeStream = regionStream.convert((r)
			-> engine.getDimension().getCaveBiomeStyle().create(rng.nextParallelRNG(1221)).stream()
				.zoom(r.getCaveBiomeZoom())
				.selectRarity(r.getCaveBiomes())
				.onNull("")
				.convertCached((s) -> {
					if(s.isEmpty())
					{
						return new IrisBiome();
					}

					return data.getBiomeLoader().load(s)
							.setInferredType(InferredType.CAVE);
				})
			).convertAware2D(ProceduralStream::get).cache2D(cacheSize);
		landBiomeStream = regionStream.convert((r)
			-> engine.getDimension().getLandBiomeStyle().create(rng.nextParallelRNG(234234234)).stream()
				.zoom(r.getLandBiomeZoom())
				.selectRarity(r.getLandBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s)
						.setInferredType(InferredType.LAND))
			).convertAware2D(ProceduralStream::get)
				.cache2D(cacheSize);
		seaBiomeStream = regionStream.convert((r)
			-> engine.getDimension().getSeaBiomeStyle().create(rng.nextParallelRNG(11232323)).stream()
				.zoom(r.getSeaBiomeZoom())
				.selectRarity(r.getSeaBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s)
						.setInferredType(InferredType.SEA))
			).convertAware2D(ProceduralStream::get)
				.cache2D(cacheSize);
		shoreBiomeStream = regionStream.convert((r)
			-> engine.getDimension().getShoreBiomeStyle().create(rng.nextParallelRNG(7787845)).stream()
				.zoom(r.getShoreBiomeZoom())
				.selectRarity(r.getShoreBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s)
						.setInferredType(InferredType.SHORE))
			).convertAware2D(ProceduralStream::get).cache2D(cacheSize);
		bridgeStream = engine.getDimension().getContinentalStyle().create(rng.nextParallelRNG(234234565)).bake().scale(1D / engine.getDimension().getContinentZoom()).bake().stream()
			.convert((v) -> v >= engine.getDimension().getLandChance() ? InferredType.SEA : InferredType.LAND);
		baseBiomeStream = bridgeStream.convertAware2D((t, x, z) -> t.equals(InferredType.SEA)
			? seaBiomeStream.get(x, z) : landBiomeStream.get(x, z))
			.convertAware2D(this::implode).cache2D(cacheSize);
		heightStream = ProceduralStream.of((x, z) -> {
			IrisBiome b = baseBiomeStream.get(x, z);
			return getHeight(engine, b, x, z, engine.getWorld().getSeed());
		}, Interpolated.DOUBLE).cache2D(cacheSize);
		slopeStream = heightStream.slope(3).interpolate().bilinear(3, 3).cache2D(cacheSize);
		objectChanceStream = ProceduralStream.ofDouble((x, z) -> {
			AtomicDouble str = new AtomicDouble(1D);
			engine.getFramework().getEngineParallax().forEachFeature(x, z, (i)
					-> str.set(Math.min(str.get(), i.getObjectChanceModifier(x, z))));
			return str.get();
		});
		trueBiomeStream = heightStream
				.convertAware2D((h, x, z) ->
					fixBiomeType(h, baseBiomeStream.get(x, z),
							regionStream.get(x, z), x, z, fluidHeight)).cache2D(cacheSize);
		trueBiomeDerivativeStream = trueBiomeStream.convert(IrisBiome::getDerivative).cache2D(cacheSize);
		heightFluidStream = heightStream.max(fluidHeight).cache2D(cacheSize);
		maxHeightStream = ProceduralStream.ofDouble((x, z) -> height);
		terrainSurfaceDecoration = trueBiomeStream
				.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.NONE));
		terrainCeilingDecoration = trueBiomeStream
				.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.CEILING));
		terrainCaveSurfaceDecoration = caveBiomeStream
				.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.NONE));
		terrainCaveCeilingDecoration = caveBiomeStream
				.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.CEILING));
		shoreSurfaceDecoration = trueBiomeStream
			.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.SHORE_LINE));
		seaSurfaceDecoration = trueBiomeStream
			.convertAware2D((b, xx,zz) -> decorateFor(b, xx, zz, DecorationPart.SEA_SURFACE));
		trueHeightStream = ProceduralStream.of((x, z) -> {
			int rx = (int) Math.round(engine.modifyX(x));
			int rz = (int) Math.round(engine.modifyZ(z));
			int heightf = (int) Math.round(getHeightStream().get(rx, rz));
			int m = heightf;

			if(engine.getDimension().isCarving())
			{
				if(engine.getDimension().isCarved(rx, m, rz, ((IrisTerrainActuator)engine.getFramework().getTerrainActuator()).getRng(), heightf))
				{
					m--;

					while(engine.getDimension().isCarved(rx, m, rz, ((IrisTerrainActuator)engine.getFramework().getTerrainActuator()).getRng(), heightf))
					{
						m--;
					}
				}
			}

			if(engine.getDimension().isCaves())
			{
				KList<CaveResult> caves = ((IrisCaveModifier)engine.getFramework().getCaveModifier()).genCaves(rx, rz, 0, 0, null);
				boolean again = true;

				while(again)
				{
					again = false;
					for(CaveResult i : caves)
					{
						if(i.getCeiling() > m && i.getFloor() < m)
						{
							m = i.getFloor();
							again = true;
						}
					}
				}
			}

			return m;
		}, Interpolated.INT).cache2D(cacheSize);
		//@done
	}

	private IrisDecorator decorateFor(IrisBiome b, double x, double z, DecorationPart part)
	{
		RNG rngc = chunkRngStream.get(x, z);

		for(IrisDecorator i : b.getDecorators())
		{
			if(!i.getPartOf().equals(part))
			{
				continue;
			}

			BlockData block = i.getBlockData(b, rngc, x, z, data);

			if(block != null)
			{
				return i;
			}
		}

		return null;
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

		if(height >= fluidHeight-1 && height <= fluidHeight + sh && !biome.isShore())
		{
			return shoreBiomeStream.get(x, z);
		}

		if(height > fluidHeight + sh && !biome.isLand())
		{
			return landBiomeStream.get(x, z);
		}

		if(height < fluidHeight && !biome.isAquatic())
		{
			return seaBiomeStream.get(x, z);
		}

		if(height == fluidHeight && !biome.isShore())
		{
			return shoreBiomeStream.get(x, z);
		}

		return biome;
	}

	private double getHeight(Engine engine, IrisBiome b, double x, double z, long seed)
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

		AtomicDouble noise = new AtomicDouble(h + fluidHeight + overlayStream.get(x,z));
		engine.getFramework().getEngineParallax().forEachFeature(x, z, (i)
				-> noise.set(i.filter(x, z, noise.get())));
		return Math.min(engine.getHeight(), Math.max(noise.get(), 0));
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
