package ninja.bytecode.iris.atomics;

import java.io.Serializable;
import java.lang.reflect.Field;

import sun.misc.Unsafe;

@SuppressWarnings("restriction")
public class AtomicCharArray implements Serializable
{
	private static final long serialVersionUID = 2862133569453604235L;
	private static final Unsafe unsafe;
	private static final int base;
	private static final int shift;
	volatile char[] array;

	public AtomicCharArray(int var1)
	{
		this.array = new char[var1];
	}

	private long checkedByteOffset(int var1)
	{
		if(var1 >= 0 && var1 < this.array.length)
		{
			return byteOffset(var1);
		}
		else
		{
			throw new IndexOutOfBoundsException("index " + var1);
		}
	}

	public final char get(int var1)
	{
		return this.getRaw(this.checkedByteOffset(var1));
	}

	private char getRaw(long var1)
	{
		return unsafe.getCharVolatile(this.array, var1);
	}

	public final void set(int var1, char var2)
	{
		unsafe.putCharVolatile(this.array, this.checkedByteOffset(var1), var2);
	}

	private static long byteOffset(int var0)
	{
		return ((long) var0 << shift) + (long) base;
	}

	static
	{
		Field f;
		Unsafe o = null;
		
		try
		{
			f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			o = (Unsafe) f.get(null);
		}
		
		catch(Throwable e)
		{
			e.printStackTrace();	
		}
		
		unsafe = o;
		base = unsafe.arrayBaseOffset(int[].class);
		int var0 = unsafe.arrayIndexScale(int[].class);
		if((var0 & var0 - 1) != 0)
		{
			throw new Error("data type scale not a power of two");
		}
		else
		{
			shift = 31 - Integer.numberOfLeadingZeros(var0);
		}
	}
}