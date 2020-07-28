package ninja.bytecode.iris.util;

import org.bukkit.block.Biome;

public class PhasicBiomeStorage
{
	private static final int e = (int) Math.round(Math.log(16.0D) / Math.log(2.0D)) - 2;
	private static final int f = (int) Math.round(Math.log(256.0D) / Math.log(2.0D)) - 2;
	public static final int a;
	public static final int b;
	public static final int c;
	private final Biome[] g;

	static
	{
		a = 1 << e + e + f;
		b = (1 << e) - 1;
		c = (1 << f) - 1;
	}

	public PhasicBiomeStorage(Biome[] abiomebase)
	{
		this.g = abiomebase;
	}

	public PhasicBiomeStorage()
	{
		this(new Biome[a]);
	}

	public static int clamp(int var0, int var1, int var2)
	{
		if(var0 < var1)
		{
			return var1;
		}
		else
		{
			return var0 > var2 ? var2 : var0;
		}
	}

	public Biome getBiome(int i, int j, int k)
	{
		int l = i & b;
		int i1 = clamp(j, 0, c);
		int j1 = k & b;
		return this.g[i1 << e + e | j1 << e | l];
	}

	public void setBiome(int i, int j, int k, Biome biome)
	{
		int l = i & b;
		int i1 = clamp(j, 0, c);
		int j1 = k & b;
		this.g[i1 << e + e | j1 << e | l] = biome;
	}
}
