package com.volmit.iris.util;

import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

public class MathHelper
{
	public static final float a = MathHelper.c(2.0f);
	private static final float[] b = (float[]) a((Object) new float[65536], var0 ->
	{
		for(int var1 = 0; var1 < ((float[]) var0).length; ++var1)
		{
			((float[]) var0)[var1] = (float) Math.sin((double) var1 * 3.141592653589793 * 2.0 / 65536.0);
		}
	});

	public static <T> T a(T var0, Consumer<T> var1)
	{
		var1.accept(var0);
		return var0;
	}

	private static final Random c = new Random();
	private static final int[] d = new int[] {0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9};
	private static final double e = Double.longBitsToDouble(4805340802404319232L);
	private static final double[] f = new double[257];
	private static final double[] g = new double[257];

	public static float sin(float var0)
	{
		return b[(int) (var0 * 10430.378f) & 65535];
	}

	public static float cos(float var0)
	{
		return b[(int) (var0 * 10430.378f + 16384.0f) & 65535];
	}

	public static float c(float var0)
	{
		return (float) Math.sqrt(var0);
	}

	public static float sqrt(double var0)
	{
		return (float) Math.sqrt(var0);
	}

	public static int d(float var0)
	{
		int var1 = (int) var0;
		return var0 < (float) var1 ? var1 - 1 : var1;
	}

	public static int floor(double var0)
	{
		int var2 = (int) var0;
		return var0 < (double) var2 ? var2 - 1 : var2;
	}

	public static long d(double var0)
	{
		long var2 = (long) var0;
		return var0 < (double) var2 ? var2 - 1L : var2;
	}

	public static float e(float var0)
	{
		return Math.abs(var0);
	}

	public static int a(int var0)
	{
		return Math.abs(var0);
	}

	public static int f(float var0)
	{
		int var1 = (int) var0;
		return var0 > (float) var1 ? var1 + 1 : var1;
	}

	public static int f(double var0)
	{
		int var2 = (int) var0;
		return var0 > (double) var2 ? var2 + 1 : var2;
	}

	public static int clamp(int var0, int var1, int var2)
	{
		if(var0 < var1)
		{
			return var1;
		}
		if(var0 > var2)
		{
			return var2;
		}
		return var0;
	}

	public static float a(float var0, float var1, float var2)
	{
		if(var0 < var1)
		{
			return var1;
		}
		if(var0 > var2)
		{
			return var2;
		}
		return var0;
	}

	public static double a(double var0, double var2, double var4)
	{
		if(var0 < var2)
		{
			return var2;
		}
		if(var0 > var4)
		{
			return var4;
		}
		return var0;
	}

	public static double b(double var0, double var2, double var4)
	{
		if(var4 < 0.0)
		{
			return var0;
		}
		if(var4 > 1.0)
		{
			return var2;
		}
		return MathHelper.d(var4, var0, var2);
	}

	public static double a(double var0, double var2)
	{
		if(var0 < 0.0)
		{
			var0 = -var0;
		}
		if(var2 < 0.0)
		{
			var2 = -var2;
		}
		return var0 > var2 ? var0 : var2;
	}

	public static int a(int var0, int var1)
	{
		return Math.floorDiv(var0, var1);
	}

	public static int nextInt(Random var0, int var1, int var2)
	{
		if(var1 >= var2)
		{
			return var1;
		}
		return var0.nextInt(var2 - var1 + 1) + var1;
	}

	public static float a(Random var0, float var1, float var2)
	{
		if(var1 >= var2)
		{
			return var1;
		}
		return var0.nextFloat() * (var2 - var1) + var1;
	}

	public static double a(Random var0, double var1, double var3)
	{
		if(var1 >= var3)
		{
			return var1;
		}
		return var0.nextDouble() * (var3 - var1) + var1;
	}

	public static double a(long[] var0)
	{
		long var1 = 0L;
		for(long var6 : var0)
		{
			var1 += var6;
		}
		return (double) var1 / (double) var0.length;
	}

	public static boolean b(double var0, double var2)
	{
		return Math.abs(var2 - var0) < 9.999999747378752E-6;
	}

	public static int b(int var0, int var1)
	{
		return Math.floorMod(var0, var1);
	}

	public static float g(float var0)
	{
		float var1 = var0 % 360.0f;
		if(var1 >= 180.0f)
		{
			var1 -= 360.0f;
		}
		if(var1 < -180.0f)
		{
			var1 += 360.0f;
		}
		return var1;
	}

	public static double g(double var0)
	{
		double var2 = var0 % 360.0;
		if(var2 >= 180.0)
		{
			var2 -= 360.0;
		}
		if(var2 < -180.0)
		{
			var2 += 360.0;
		}
		return var2;
	}

	public static float c(float var0, float var1)
	{
		return MathHelper.g(var1 - var0);
	}

	public static float d(float var0, float var1)
	{
		return MathHelper.e(MathHelper.c(var0, var1));
	}

	public static float b(float var0, float var1, float var2)
	{
		float var3 = MathHelper.c(var0, var1);
		float var4 = MathHelper.a(var3, -var2, var2);
		return var1 - var4;
	}

	public static float c(float var0, float var1, float var2)
	{
		var2 = MathHelper.e(var2);
		if(var0 < var1)
		{
			return MathHelper.a(var0 + var2, var0, var1);
		}
		return MathHelper.a(var0 - var2, var1, var0);
	}

	public static float d(float var0, float var1, float var2)
	{
		float var3 = MathHelper.c(var0, var1);
		return MathHelper.c(var0, var0 + var3, var2);
	}

	public static int c(int var0)
	{
		int var1 = var0 - 1;
		var1 |= var1 >> 1;
		var1 |= var1 >> 2;
		var1 |= var1 >> 4;
		var1 |= var1 >> 8;
		var1 |= var1 >> 16;
		return var1 + 1;
	}

	public static boolean d(int var0)
	{
		return var0 != 0 && (var0 & var0 - 1) == 0;
	}

	public static int e(int var0)
	{
		var0 = MathHelper.d(var0) ? var0 : MathHelper.c(var0);
		return d[(int) ((long) var0 * 125613361L >> 27) & 31];
	}

	public static int f(int var0)
	{
		return MathHelper.e(var0) - (MathHelper.d(var0) ? 0 : 1);
	}

	public static int c(int var0, int var1)
	{
		int var2;
		if(var1 == 0)
		{
			return 0;
		}
		if(var0 == 0)
		{
			return var1;
		}
		if(var0 < 0)
		{
			var1 *= -1;
		}
		if((var2 = var0 % var1) == 0)
		{
			return var0;
		}
		return var0 + var1 - var2;
	}

	public static float h(float var0)
	{
		return var0 - (float) MathHelper.d(var0);
	}

	public static double h(double var0)
	{
		return var0 - (double) MathHelper.d(var0);
	}

	public static long a(BlockPosition var0)
	{
		return c(var0.getX(), var0.getY(), var0.getZ());
	}

	public static long c(int var0, int var1, int var2)
	{
		long var3 = (long) (var0 * 3129871) ^ (long) var2 * 116129781L ^ (long) var1;
		var3 = var3 * var3 * 42317861L + var3 * 11L;
		return var3 >> 16;
	}

	public static UUID a(Random var0)
	{
		long var1 = var0.nextLong() & -61441L | 16384L;
		long var3 = var0.nextLong() & 0x3FFFFFFFFFFFFFFFL | Long.MIN_VALUE;
		return new UUID(var1, var3);
	}

	public static UUID a()
	{
		return MathHelper.a(c);
	}

	public static double c(double var0, double var2, double var4)
	{
		return (var0 - var2) / (var4 - var2);
	}

	public static double d(double var0, double var2)
	{
		double var9;
		boolean var6;
		boolean var7;
		boolean var8;
		double var4 = var2 * var2 + var0 * var0;
		if(Double.isNaN(var4))
		{
			return Double.NaN;
		}
		@SuppressWarnings("unused")
		boolean bl = var6 = var0 < 0.0;
		if(var6)
		{
			var0 = -var0;
		}
		@SuppressWarnings("unused")
		boolean bl2 = var7 = var2 < 0.0;
		if(var7)
		{
			var2 = -var2;
		}
		@SuppressWarnings("unused")
		boolean bl3 = var8 = var0 > var2;
		if(var8)
		{
			var9 = var2;
			var2 = var0;
			var0 = var9;
		}
		var9 = MathHelper.i(var4);
		double var11 = e + (var0 *= var9);
		int var13 = (int) Double.doubleToRawLongBits(var11);
		double var14 = f[var13];
		double var16 = g[var13];
		double var18 = var11 - e;
		double var20 = var0 * var16 - (var2 *= var9) * var18;
		double var22 = (6.0 + var20 * var20) * var20 * 0.16666666666666666;
		double var24 = var14 + var22;
		if(var8)
		{
			var24 = 1.5707963267948966 - var24;
		}
		if(var7)
		{
			var24 = 3.141592653589793 - var24;
		}
		if(var6)
		{
			var24 = -var24;
		}
		return var24;
	}

	public static double i(double var0)
	{
		double var2 = 0.5 * var0;
		long var4 = Double.doubleToRawLongBits(var0);
		var4 = 6910469410427058090L - (var4 >> 1);
		var0 = Double.longBitsToDouble(var4);
		var0 *= 1.5 - var2 * var0 * var0;
		return var0;
	}

	public static int f(float var0, float var1, float var2)
	{
		float var9;
		float var8;
		float var10;
		int var3 = (int) (var0 * 6.0f) % 6;
		float var4 = var0 * 6.0f - (float) var3;
		float var5 = var2 * (1.0f - var1);
		float var6 = var2 * (1.0f - var4 * var1);
		float var7 = var2 * (1.0f - (1.0f - var4) * var1);
		switch(var3)
		{
			case 0:
			{
				var8 = var2;
				var9 = var7;
				var10 = var5;
				break;
			}
			case 1:
			{
				var8 = var6;
				var9 = var2;
				var10 = var5;
				break;
			}
			case 2:
			{
				var8 = var5;
				var9 = var2;
				var10 = var7;
				break;
			}
			case 3:
			{
				var8 = var5;
				var9 = var6;
				var10 = var2;
				break;
			}
			case 4:
			{
				var8 = var7;
				var9 = var5;
				var10 = var2;
				break;
			}
			case 5:
			{
				var8 = var2;
				var9 = var5;
				var10 = var6;
				break;
			}
			default:
			{
				throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + var0 + ", " + var1 + ", " + var2);
			}
		}
		int var11 = MathHelper.clamp((int) (var8 * 255.0f), 0, 255);
		int var12 = MathHelper.clamp((int) (var9 * 255.0f), 0, 255);
		int var13 = MathHelper.clamp((int) (var10 * 255.0f), 0, 255);
		return var11 << 16 | var12 << 8 | var13;
	}

	public static int g(int var0)
	{
		var0 ^= var0 >>> 16;
		var0 *= -2048144789;
		var0 ^= var0 >>> 13;
		var0 *= -1028477387;
		var0 ^= var0 >>> 16;
		return var0;
	}

	public static int a(int var0, int var1, IntPredicate var2)
	{
		int var3 = var1 - var0;
		while(var3 > 0)
		{
			int var4 = var3 / 2;
			int var5 = var0 + var4;
			if(var2.test(var5))
			{
				var3 = var4;
				continue;
			}
			var0 = var5 + 1;
			var3 -= var4 + 1;
		}
		return var0;
	}

	public static float g(float var0, float var1, float var2)
	{
		return var1 + var0 * (var2 - var1);
	}

	public static double d(double var0, double var2, double var4)
	{
		return var2 + var0 * (var4 - var2);
	}

	public static double a(double var0, double var2, double var4, double var6, double var8, double var10)
	{
		return MathHelper.d(var2, MathHelper.d(var0, var4, var6), MathHelper.d(var0, var8, var10));
	}

	public static double a(double var0, double var2, double var4, double var6, double var8, double var10, double var12, double var14, double var16, double var18, double var20)
	{
		return MathHelper.d(var4, MathHelper.a(var0, var2, var6, var8, var10, var12), MathHelper.a(var0, var2, var14, var16, var18, var20));
	}

	public static double j(double var0)
	{
		return var0 * var0 * var0 * (var0 * (var0 * 6.0 - 15.0) + 10.0);
	}

	public static int k(double var0)
	{
		if(var0 == 0.0)
		{
			return 0;
		}
		return var0 > 0.0 ? 1 : -1;
	}

	@Deprecated
	public static float j(float var0, float var1, float var2)
	{
		float var3;
		for(var3 = var1 - var0; var3 < -180.0f; var3 += 360.0f)
		{
		}
		while(var3 >= 180.0f)
		{
			var3 -= 360.0f;
		}
		return var0 + var2 * var3;
	}

	public static float k(float var0)
	{
		return var0 * var0;
	}

	static
	{
		for(int var02 = 0; var02 < 257; ++var02)
		{
			// TODO: WARNING HEIGHT 
			double var1 = (double) var02 / 256.0;
			double var3 = Math.asin(var1);
			MathHelper.g[var02] = Math.cos(var3);
			MathHelper.f[var02] = var3;
		}
	}
}