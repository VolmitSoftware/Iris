package com.volmit.iris.util;

import java.util.concurrent.locks.ReentrantLock;

import lombok.Data;

@Data
public class IrisLock
{
	private transient final ReentrantLock lock;
	private transient final String name;
	private transient boolean disabled = false;

	public IrisLock(String name)
	{
		this.name = name;
		lock = new ReentrantLock(false);
	}

	public void lock()
	{
		if(disabled)
		{
			return;
		}

		lock.lock();
	}

	public void unlock()
	{
		if(disabled)
		{
			return;
		}
		try
		{
			lock.unlock();
		}

		catch(Throwable e)
		{

		}
	}
}
