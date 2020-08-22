package com.volmit.iris.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.StringJoiner;

public class NibbleArray implements Writable
{
	private byte[] data;
	private int depth;
	private final int size;
	private byte mask;
	private final Object lock = new Object();

	public NibbleArray(int capacity, DataInputStream in) throws IOException
	{
		size = capacity;
		read(in);
	}

	public NibbleArray(int nibbleDepth, int capacity)
	{
		if(nibbleDepth > 8 || nibbleDepth < 1)
		{
			throw new IllegalArgumentException();
		}

		int neededBits = nibbleDepth * capacity;

		size = capacity;
		depth = nibbleDepth;
		data = new byte[(neededBits + neededBits % 8) / 8];
		mask = (byte) maskFor(nibbleDepth);
	}

	public NibbleArray(int nibbleDepth, int capacity, NibbleArray existing)
	{
		if(nibbleDepth > 8 || nibbleDepth < 1)
		{
			throw new IllegalArgumentException();
		}

		int neededBits = nibbleDepth * capacity;
		size = capacity;
		depth = nibbleDepth;
		data = new byte[(neededBits + neededBits % 8) / 8];
		mask = (byte) maskFor(nibbleDepth);

		for(int i = 0; i < Math.min(size, existing.size()); i++)
		{
			set(i, existing.get(i));
		}
	}

	@Override
	public void write(DataOutputStream o) throws IOException
	{
		o.writeByte(depth + Byte.MIN_VALUE);
		o.write(data);
	}

	@Override
	public void read(DataInputStream i) throws IOException
	{
		depth = i.readByte() - Byte.MIN_VALUE;
		int neededBits = depth * size;
		data = new byte[(neededBits + neededBits % 8) / 8];
		mask = (byte) maskFor(depth);
		i.read(data);
	}

	public int size()
	{
		return size;
	}

	public byte get(int index)
	{
		synchronized(lock)
		{
			bitIndex = index * depth;
			byteIndex = bitIndex >> 3;
			bitInByte = bitIndex & 7;
			int value = data[byteIndex] >> bitInByte;

			if(bitInByte + depth > 8)
			{
				value |= data[byteIndex + 1] << bitInByte;
			}

			return (byte) (value & mask);
		}
	}

	public byte getAsync(int index)
	{
		int bitIndex = index * depth;
		int byteIndex = bitIndex >> 3;
		int bitInByte = bitIndex & 7;
		int value = data[byteIndex] >> bitInByte;

		if(bitInByte + depth > 8)
		{
			value |= data[byteIndex + 1] << bitInByte;
		}

		return (byte) (value & mask);
	}

	private transient int bitIndex, byteIndex, bitInByte;

	public void set(int index, int nibble)
	{
		set(index, (byte) nibble);
	}

	public void set(int index, byte nybble)
	{
		synchronized(lock)
		{
			bitIndex = index * depth;
			byteIndex = bitIndex >> 3;
			bitInByte = bitIndex & 7;
			data[byteIndex] = (byte) (((~(data[byteIndex] & (mask << bitInByte)) & data[byteIndex]) | ((nybble & mask) << bitInByte)) & 0xff);

			if(bitInByte + depth > 8)
			{
				data[byteIndex + 1] = (byte) (((~(data[byteIndex + 1] & MASKS[bitInByte + depth - 8]) & data[byteIndex + 1]) | ((nybble & mask) >> (8 - bitInByte))) & 0xff);
			}
		}
	}

	public String toBitsString()
	{
		return toBitsString(ByteOrder.BIG_ENDIAN);
	}

	public String toBitsString(ByteOrder byteOrder)
	{
		StringJoiner joiner = new StringJoiner(" ");

		for(int i = 0; i < data.length; i++)
		{
			joiner.add(binaryString(data[i], byteOrder));
		}

		return joiner.toString();
	}

	public void clear()
	{
		Arrays.fill(data, (byte) 0);
	}

	public void setAll(byte nibble)
	{
		for(int i = 0; i < size; i++)
		{
			set(i, nibble);
		}
	}

	public void setAll(int nibble)
	{
		for(int i = 0; i < size; i++)
		{
			set(i, (byte) nibble);
		}
	}

	public static int maskFor(int amountOfBits)
	{
		return powerOfTwo(amountOfBits) - 1;
	}

	public static int powerOfTwo(int power)
	{
		int result = 1;

		for(int i = 0; i < power; i++)
		{
			result *= 2;
		}

		return result;
	}

	private static final int[] MASKS = new int[8];

	static
	{
		for(int i = 0; i < MASKS.length; i++)
		{
			MASKS[i] = maskFor(i);
		}
	}

	public static String binaryString(byte b, ByteOrder byteOrder)
	{
		String str = String.format("%8s", Integer.toBinaryString(b & 0xff)).replace(' ', '0');

		return byteOrder.equals(ByteOrder.BIG_ENDIAN) ? str : reverse(str);
	}

	public static String reverse(String str)
	{
		return new StringBuilder(str).reverse().toString();
	}
}