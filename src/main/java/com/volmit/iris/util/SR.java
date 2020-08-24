package com.volmit.iris.util;

public abstract class SR implements Runnable, CancellableTask
{
	private int id = 0;

	public SR()
	{
		this(0);
	}

	public SR(int interval)
	{
		id = J.sr(this, interval);
	}

	@Override
	public void cancel()
	{
		J.csr(id);
	}

	public int getId()
	{
		return id;
	}
}
