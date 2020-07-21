package ninja.bytecode.iris.object;

import org.bukkit.World.Environment;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.Desc;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Desc("Represents a dimension")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisDimension extends IrisRegistrant
{
	@Desc("The human readable name of this dimension")
	private String name = "A Dimension";

	@Desc("The interpolation function for splicing noise maxes together")
	private InterpolationMethod interpolationFunction = InterpolationMethod.BICUBIC;

	@Desc("The interpolation distance scale. Increase = more smooth, less detail")
	private double interpolationScale = 63;

	private double caveThickness = 1D;
	private double caveScale = 1D;
	private double caveShift = 0D;
	private boolean caves = true;

	@Desc("The world environment")
	private Environment environment = Environment.NORMAL;

	@Desc("Define all of the regions to include in this dimension. Dimensions -> Regions -> Biomes -> Objects etc")
	private KList<String> regions = new KList<>();

	@Desc("The fluid height for this dimension")
	private int fluidHeight = 63;

	@Desc("Keep this either undefined or empty. Setting any biome name into this will force iris to only generate the specified biome. Great for testing.")
	private String focus = "";

	@Desc("Zoom in or out the biome size. Higher = bigger biomes")
	private double biomeZoom = 5D;

	@Desc("Zoom in or out the terrain. This stretches the terrain. Due to performance improvements, Higher than 2.0 may cause weird rounding artifacts. Lower = more terrain changes per block. Its a true zoom-out.")
	private double terrainZoom = 2D;

	@Desc("You can rotate the input coordinates by an angle. This can make terrain appear more natural (less sharp corners and lines). This literally rotates the entire dimension by an angle. Hint: Try 12 degrees or something not on a 90 or 45 degree angle.")
	private double dimensionAngleDeg = 0;

	@Desc("Iris adds a few roughness filters to noise. Increasing this smooths it out. Decreasing this makes it bumpier/scratchy")
	private double roughnessZoom = 2D;

	@Desc("The height of the roughness filters")
	private int roughnessHeight = 3;

	@Desc("Coordinate fracturing applies noise to the input coordinates. This creates the 'iris swirls' and wavy features. The distance pushes these waves further into places they shouldnt be. This is a block value multiplier.")
	private double coordFractureDistance = 20;

	@Desc("Coordinate fracturing zoom. Higher = less frequent warping, Lower = more frequent and rapid warping / swirls.")
	private double coordFractureZoom = 8;

	@Desc("This zooms in the land space")
	private double landZoom = 1;

	@Desc("This can zoom the shores")
	private double shoreZoom = 1;

	@Desc("This zooms oceanic biomes")
	private double seaZoom = 1;

	@Desc("Zoom in continents")
	private double continentZoom = 1;

	@Desc("Change the size of regions")
	private double regionZoom = 1;

	@Desc("Disable this to stop placing schematics in biomes")
	private boolean placeObjects = true;

	private transient CNG coordFracture;
	private transient Double sinr;
	private transient Double cosr;
	private transient Double rad;

	public CNG getCoordFracture(RNG rng, int signature)
	{
		if(coordFracture == null)
		{
			coordFracture = CNG.signature(rng.nextParallelRNG(signature));
			coordFracture.scale(0.012 / coordFractureZoom);
		}

		return coordFracture;
	}

	public double getDimensionAngle()
	{
		if(rad == null)
		{
			rad = Math.toRadians(dimensionAngleDeg);
		}

		return rad;
	}

	public double sinRotate()
	{
		if(sinr == null)
		{
			sinr = Math.sin(getDimensionAngle());
		}

		return sinr;
	}

	public double cosRotate()
	{
		if(cosr == null)
		{
			cosr = Math.cos(getDimensionAngle());
		}

		return cosr;
	}
}
