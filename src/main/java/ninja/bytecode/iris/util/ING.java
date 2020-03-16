package ninja.bytecode.iris.util;

public class ING
{
	private SNG base;
	private SNG[] children;

	public ING(RNG rng, int detail)
	{
		assert (detail >= 1);
		this.children = new SNG[detail];

		for(int i = 0; i < detail; i++)
		{
			children[i] = new SNG(rng.nextParallelRNG((i * 368989) % 13345));
		}
		base = new SNG(rng.nextParallelRNG(13));
	}

	public double noise(double x, double z)
	{
		double cx = x;
		double cz = z;
		int i;
		double j;
		double k;

		for(i = 0; i < children.length; i++)
		{
			j = (i + 1) * 2;
			k = (i + 1) * 1;
			cx = cx + (children[i].noise((cx / j) + i, (cz / j) - i) * k);
			cz = cz - (children[i].noise((cz / j) - i, (cx / j) + i) * k);
		}

		return (base.noise(cx, cz) / 2D) + 0.5D;
	}
}
