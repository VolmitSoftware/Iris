package ninja.bytecode.iris.util;

public class CNG
{
	public static long hits = 0;
	public static long creates = 0;
	public static final NoiseInjector ADD = (s, v) -> new double[] {s + v, 1};
	public static final NoiseInjector SRC_SUBTRACT = (s, v) -> new double[] {s - v < 0 ? 0 : s - v, -1};
	public static final NoiseInjector DST_SUBTRACT = (s, v) -> new double[] {v - s < 0 ? 0 : s - v, -1};
	public static final NoiseInjector MULTIPLY = (s, v) -> new double[] {s * v, 0};
	public static final NoiseInjector MAX = (s, v) -> new double[] {Math.max(s, v), 0};
	public static final NoiseInjector MIN = (s, v) -> new double[] {Math.min(s, v), 0};
	public static final NoiseInjector SRC_MOD = (s, v) -> new double[] {s % v, 0};
	public static final NoiseInjector SRC_POW = (s, v) -> new double[] {Math.pow(s, v), 0};
	public static final NoiseInjector DST_MOD = (s, v) -> new double[] {v % s, 0};
	public static final NoiseInjector DST_POW = (s, v) -> new double[] {Math.pow(v, s), 0};
	private double freq;
	private double amp;
	private double scale;
	private double fscale;
	private KList<CNG> children;
	private CNG fracture;
	private SNG generator;
	private final double opacity;
	private NoiseInjector injector;
	private RNG rng;
	private int oct;
	private double patch;
	private double up;
	private double down;
	private double power;

	public static CNG signature(RNG rng)
	{
		//@builder
		return new CNG(rng.nextParallelRNG(17), 1D, 8)
				.scale(0.012)
				.fractureWith(new CNG(rng.nextParallelRNG(18), 1, 5)
					.scale(0.018)
					.child(new CNG(rng.nextParallelRNG(19), 1, 2)
						.scale(0.1))
					.fractureWith(new CNG(rng.nextParallelRNG(20), 1, 3)
						.scale(0.15), 24), 44).down(0.3).patch(2.5);
		//@done
	}

	public CNG(RNG random)
	{
		this(random, 1);
	}

	public CNG(RNG random, int octaves)
	{
		this(random, 1D, octaves);
	}

	public CNG(RNG random, double opacity, int octaves)
	{
		creates += octaves;
		this.oct = octaves;
		this.rng = random;
		power = 1;
		freq = 1;
		amp = 1;
		scale = 1;
		patch = 1;
		fscale = 1;
		fracture = null;
		generator = new SNG(random);
		this.opacity = opacity;
		this.injector = ADD;
	}

	public CNG child(CNG c)
	{
		if(children == null)
		{
			children = new KList<>();
		}

		children.add(c);
		return this;
	}

	@Deprecated
	public RNG nextRNG()
	{
		return getRNG().nextRNG();
	}

	public RNG getRNG()
	{
		return rng;
	}

	public CNG fractureWith(CNG c, double scale)
	{
		fracture = c;
		fscale = scale;
		return this;
	}

	public CNG scale(double c)
	{
		scale = c;
		return this;
	}

	public CNG freq(double c)
	{
		freq = c;
		return this;
	}

	public CNG amp(double c)
	{
		amp = c;
		return this;
	}

	public CNG patch(double c)
	{
		patch = c;
		return this;
	}

	public CNG up(double c)
	{
		up = c;
		return this;
	}

	public CNG down(double c)
	{
		down = c;
		return this;
	}

	public CNG injectWith(NoiseInjector i)
	{
		injector = i;
		return this;
	}

	public int fit(int min, int max, double... dim)
	{
		if(min == max)
		{
			return min;
		}

		double noise = noise(dim);

		return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
	}

	public int fitDouble(double min, double max, double... dim)
	{
		if(min == max)
		{
			return (int) Math.round(min);
		}

		double noise = noise(dim);

		return (int) Math.round(IrisInterpolation.lerp(min, max, noise));
	}

	public int fitDoubleExponent(double min, double max, double exponent, double... dim)
	{
		if(min == max)
		{
			return (int) Math.round(min);
		}

		double noise = noise(dim);

		return (int) Math.round(IrisInterpolation.lerp(min, max, exponent == 1 ? noise : Math.pow(noise, exponent)));
	}

	public double noise(double... dim)
	{
		double f = fracture != null ? (fracture.noise(dim) - 0.5) * fscale : 0D;
		double x = dim.length > 0 ? dim[0] + f : 0D;
		double y = dim.length > 1 ? dim[1] - f : 0D;
		double z = dim.length > 2 ? dim[2] + f : 0D;
		double n = ((generator.noise(x * scale, y * scale, z * scale, oct, freq, amp, true) / 2D) + 0.5D) * opacity;
		n = power != 1D ? Math.pow(n, power) : n;
		double m = 1;
		hits += oct;
		if(children == null)
		{
			return (n - down + up) * patch;
		}

		for(CNG i : children)
		{
			double[] r = injector.combine(n, i.noise(dim));
			n = r[0];
			m += r[1];
		}

		return ((n / m) - down + up) * patch;
	}

	public CNG pow(double power)
	{
		this.power = power;
		return this;
	}
}
