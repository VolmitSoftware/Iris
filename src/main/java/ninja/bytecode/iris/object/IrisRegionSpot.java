package ninja.bytecode.iris.object;

import lombok.Data;
import ninja.bytecode.iris.util.CellGenerator;
import ninja.bytecode.iris.util.Desc;
import ninja.bytecode.iris.util.RNG;

@Desc("A spot config")
@Data
public class IrisRegionSpot
{
	@Desc("The biome to be placed")
	private String biome;
	@Desc("Where this spot overrides. Land sea or shore")
	private InferredType type = InferredType.LAND;
	@Desc("What type this spot is (i.e. target SEA but as LAND) like an island. Default matches the target type")
	private InferredType as = InferredType.DEFER;
	@Desc("The scale of splotches")
	private double scale = 1;
	@Desc("Rarity is how often this splotch appears. higher = less often")
	private double rarity = 1;
	@Desc("The shuffle or how natural the splotch looks like (anti-polygon)")
	private double shuffle = 128;

	private transient CellGenerator spot;

	public IrisRegionSpot()
	{

	}

	public boolean isSpot(RNG rng, double x, double z)
	{
		if(spot == null)
		{
			spot = new CellGenerator(rng.nextParallelRNG(168583 * hashCode()));
			spot.setCellScale(scale);
			spot.setShuffle(shuffle);
		}

		if(spot.getIndex(x, z, (int) (Math.round(rarity) + 8)) == (int) ((Math.round(rarity) + 8) / 2))
		{
			return true;
		}

		return false;
	}
}
