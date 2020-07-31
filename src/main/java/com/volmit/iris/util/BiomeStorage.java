package com.volmit.iris.util;

import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator.BiomeGrid;

public class BiomeStorage
{
	private static final int e;
	private static final int f;
	public static final int a;
	public static final int b;
	public static final int c;
	private final Biome[] g;

	static
	{
		e = (int) Math.round(Math.log(16.0) / Math.log(2.0)) - 2;
		f = (int) Math.round(Math.log(256.0) / Math.log(2.0)) - 2;
		a = 1 << BiomeStorage.e + BiomeStorage.e + BiomeStorage.f;
		b = (1 << BiomeStorage.e) - 1;
		c = (1 << BiomeStorage.f) - 1;
	}

	public BiomeStorage(final Biome[] aBiome)
	{
		this.g = aBiome;
	}

	public BiomeStorage()
	{
		this(new Biome[BiomeStorage.a]);
	}

	public BiomeStorage b()
	{
		return new BiomeStorage(this.g.clone());
	}

	public void inject(BiomeGrid grid)
	{
		for(int i = 0; i < 256; i++)
		{
			for(int j = 0; j < 16; j++)
			{
				for(int k = 0; k < 16; k++)
				{
					Biome b = getBiome(j, i, k);

					if(b == null || b.equals(Biome.THE_VOID))
					{
						continue;
					}

					grid.setBiome(j, i, k, b);
				}
			}
		}
	}

	public Biome getBiome(final int i, final int j, final int k)
	{
		final int l = i & BiomeStorage.b;
		final int i2 = MathHelper.clamp(j, 0, BiomeStorage.c);
		final int j2 = k & BiomeStorage.b;
		return this.g[i2 << BiomeStorage.e + BiomeStorage.e | j2 << BiomeStorage.e | l];
	}

	public void setBiome(final int i, final int j, final int k, final Biome biome)
	{
		final int l = i & BiomeStorage.b;
		final int i2 = MathHelper.clamp(j, 0, BiomeStorage.c);
		final int j2 = k & BiomeStorage.b;
		this.g[i2 << BiomeStorage.e + BiomeStorage.e | j2 << BiomeStorage.e | l] = biome;
	}
}