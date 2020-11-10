package com.volmit.iris.scaffold.stream.utility;

import com.volmit.iris.scaffold.stream.BasicStream;
import com.volmit.iris.scaffold.stream.ProceduralStream;

import java.util.concurrent.Semaphore;

public class SemaphoreStream<T> extends BasicStream<T>
{
	private final Semaphore semaphore;

	public SemaphoreStream(ProceduralStream<T> stream, int permits)
	{
		super(stream);
		this.semaphore = new Semaphore(permits);
	}

	@Override
	public double toDouble(T t)
	{
		return getTypedSource().toDouble(t);
	}

	@Override
	public T fromDouble(double d)
	{
		return getTypedSource().fromDouble(d);
	}

	@Override
	public T get(double x, double z)
	{
		synchronized (getTypedSource())
		{
			return getTypedSource().get(x, z);
		}
	}

	@Override
	public T get(double x, double y, double z)
	{
		synchronized (getTypedSource())
		{
			return getTypedSource().get(x, y, z);
		}
	}
}
