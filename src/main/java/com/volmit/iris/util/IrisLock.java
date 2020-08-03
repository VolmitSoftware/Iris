package com.volmit.iris.util;

import java.util.concurrent.locks.ReentrantLock;

import lombok.Data;

@Data
public class IrisLock
{
	private final ReentrantLock lock;
	private final String name;
	private boolean disabled = false;

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
		lock.unlock();
	}
}
