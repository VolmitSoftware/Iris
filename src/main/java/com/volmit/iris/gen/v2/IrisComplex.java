package com.volmit.iris.gen.v2;

import com.volmit.iris.gen.v2.scaffold.layer.ProceduralStream;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.object.InferredType;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Data
public class IrisComplex
{
	private ProceduralStream<IrisRegion> regionStream;
	private ProceduralStream<InferredType> bridgeStream;
	private ProceduralStream<IrisBiome> landBiomeStream;
	private ProceduralStream<IrisBiome> seaBiomeStream;
	private ProceduralStream<IrisBiome> shoreBiomeStream;
	private ProceduralStream<IrisBiome> baseBiomeStream;

	public IrisComplex()
	{

	}

	public ProceduralStream<IrisBiome> getBiomeStream(InferredType type)
	{
		switch(type)
		{
			case CAVE:
				break;
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
		RNG rng = new RNG(seed);
		//@builder
		regionStream = NoiseStyle.CELLULAR.stream(rng.nextRNG())
			.zoom(4)
			.select(dimension.getRegions())
			.convertCached((s) -> data.getRegionLoader().load(s));
		landBiomeStream = regionStream.convertCached((r) 
			-> NoiseStyle.CELLULAR.stream(new RNG((long) (seed + 10000 * r.getLandBiomeZoom())))
				.zoom(r.getLandBiomeZoom())
				.select(r.getLandBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s))
			).convertAware2D((str, x, z) -> str.get(x, z));
		seaBiomeStream = regionStream.convertCached((r) 
			-> NoiseStyle.CELLULAR.stream(new RNG((long) (seed + 20000 * r.getSeaBiomeZoom())))
				.zoom(r.getSeaBiomeZoom())
				.select(r.getSeaBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s))
			).convertAware2D((str, x, z) -> str.get(x, z));
		shoreBiomeStream = regionStream.convertCached((r) 
			-> NoiseStyle.CELLULAR.stream(new RNG((long) (seed + 30000 * r.getShoreBiomeZoom())))
				.zoom(r.getShoreBiomeZoom())
				.select(r.getShoreBiomes())
				.convertCached((s) -> data.getBiomeLoader().load(s))
			).convertAware2D((str, x, z) -> str.get(x, z));
		bridgeStream = NoiseStyle.CELLULAR.stream(new RNG(seed + 4000))
				.convert((v) -> v >= dimension.getLandChance() ? InferredType.SEA : InferredType.LAND);
		baseBiomeStream = bridgeStream.convertAware2D((t, x, z) -> t.equals(InferredType.SEA) 
				? seaBiomeStream.get(x, z) : landBiomeStream.get(x, z));
		//@done
	}
}
