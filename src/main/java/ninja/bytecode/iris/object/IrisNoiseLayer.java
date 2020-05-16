package ninja.bytecode.iris.object;

import java.util.concurrent.locks.ReentrantLock;

import ninja.bytecode.iris.util.CNG;
import ninja.bytecode.iris.util.RNG;

public class IrisNoiseLayer
{
	private double zoom;
	private double offsetX;
	private double offsetZ;
	private long seed;
	private double min;
	private double max;
	private ReentrantLock lock;

	private transient CNG generator;

	public IrisNoiseLayer()
	{
		lock = new ReentrantLock();
	}

	protected CNG getGenerator(long superSeed)
	{
		if(generator == null)
		{
			lock.lock();
			generator = CNG.signature(new RNG(superSeed + 33955677 - seed));
			lock.unlock();
		}

		return generator;
	}

	public double getNoise(long superSeed, double x, double z)
	{
		return getGenerator(superSeed).fitDoubleD(min, max, (x / zoom) + offsetX, (z / zoom) + offsetZ);
	}
}
