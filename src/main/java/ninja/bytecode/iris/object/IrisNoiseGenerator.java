package ninja.bytecode.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import lombok.Data;
import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.IrisInterpolation;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Data
public class IrisNoiseGenerator
{
	private double zoom = 1;
	private double opacity = 1;
	private double offsetX = 0;
	private double offsetY = 0;
	private double offsetZ = 0;
	private long seed = 0;
	private boolean parametric = false;
	private boolean bezier = false;
	private boolean sinCentered = false;
	private double exponent = 1;
	private boolean enabled = true;
	private boolean irisBased = true;
	private int octaves = 1;
	private KList<IrisNoiseGenerator> fracture = new KList<>();

	private transient ReentrantLock lock;
	private transient CNG generator;

	public IrisNoiseGenerator()
	{
		lock = new ReentrantLock();
	}

	public IrisNoiseGenerator(boolean enabled)
	{
		this();
		this.enabled = enabled;
	}

	protected CNG getGenerator(long superSeed)
	{
		if(generator == null)
		{
			lock.lock();
			generator = irisBased ? CNG.signature(new RNG(superSeed + 33955677 - seed)) : new CNG(new RNG(superSeed + 33955677 - seed), 1D, octaves);
			lock.unlock();
		}

		return generator;
	}

	public double getMax()
	{
		return getOffsetY() + opacity;
	}

	public double getNoise(long superSeed, double xv, double zv)
	{
		if(!enabled)
		{
			return offsetY;
		}

		double x = xv;
		double z = zv;
		int g = 33;

		for(IrisNoiseGenerator i : fracture)
		{
			if(i.isEnabled())
			{
				x += i.getNoise(superSeed + seed + g, xv, zv);
				z -= i.getNoise(superSeed + seed + g, zv, xv);
			}
			g += 819;
		}

		double n = getGenerator(superSeed).fitDoubleD(0, opacity, (x / zoom) + offsetX, (z / zoom) + offsetZ);
		n = (exponent != 1 ? Math.pow(n, exponent) : n) + offsetY;
		n = parametric ? IrisInterpolation.parametric(n, 1) : n;
		n = bezier ? IrisInterpolation.bezier(n) : n;
		n = sinCentered ? IrisInterpolation.sinCenter(n) : n;

		return n;
	}
}
