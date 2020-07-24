package ninja.bytecode.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.Desc;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Desc("Represents an iris region")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisRegion extends IrisRegistrant
{
	@Desc("The name of the region")
	private String name = "A Region";
	@Desc("The shore ration (How much percent of land should be a shore)")
	private double shoreRatio = 0.13;

	@Desc("The min shore height")
	private double shoreHeightMin = 1.2;

	@Desc("The the max shore height")
	private double shoreHeightMax = 3.2;

	@Desc("The varience of the shore height")
	private double shoreHeightZoom = 3.14;

	@Desc("The biome implosion ratio, how much to implode biomes into children (chance)")
	private double biomeImplosionRatio = 0.4;

	@Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
	private KList<String> landBiomes = new KList<>();

	@Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
	private KList<String> seaBiomes = new KList<>();

	@Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
	private KList<String> shoreBiomes = new KList<>();

	@Desc("Ridge biomes create a vein-like network like rivers through this region")
	private KList<IrisRegionRidge> ridgeBiomes = new KList<>();

	@Desc("Spot biomes splotch themselves across this region like lakes")
	private KList<IrisRegionSpot> spotBiomes = new KList<>();

	@Desc("Define regional deposit generators that add onto the global deposit generators")
	private KList<IrisDepositGenerator> deposits = new KList<>();

	private transient CNG shoreHeightGenerator;
	private transient ReentrantLock lock = new ReentrantLock();

	public double getShoreHeight(double x, double z)
	{
		if(shoreHeightGenerator == null)
		{
			lock.lock();
			shoreHeightGenerator = CNG.signature(new RNG(hashCode()));
			lock.unlock();
		}

		return shoreHeightGenerator.fitDoubleD(shoreHeightMin, shoreHeightMax, x / shoreHeightZoom, z / shoreHeightZoom);
	}
}
