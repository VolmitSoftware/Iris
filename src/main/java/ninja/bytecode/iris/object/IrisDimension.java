package ninja.bytecode.iris.object;

import org.bukkit.World.Environment;

import lombok.Data;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.KList;
import ninja.bytecode.iris.util.RNG;

@Data
public class IrisDimension
{
	private String name = "A Dimension";
	private InterpolationMethod interpolationFunction = InterpolationMethod.BICUBIC;
	private double interpolationScale = 63;
	private InterpolationMethod interpolationSurfaceFunction = InterpolationMethod.BICUBIC;
	private double interpolationSurfaceScale = 4;
	private Environment environment = Environment.NORMAL;
	private KList<String> regions = new KList<>();
	private int fluidHeight = 127;
	private double biomeZoom = 5D;
	private double terrainZoom = 2D;
	private double dimensionAngleDeg = 0;
	private double roughnessZoom = 2D;
	private int roughnessHeight = 3;
	private double coordFractureDistance = 20;
	private double coordFractureZoom = 8;
	private double landZoom = 1;
	private double shoreZoom = 1;
	private double seaZoom = 1;
	private double continentZoom = 1;
	private double regionZoom = 1;

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
