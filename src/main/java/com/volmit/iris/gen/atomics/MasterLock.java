package com.volmit.iris.object.atomics;

import java.util.concurrent.locks.ReentrantLock;

import com.volmit.iris.util.KMap;

public class MasterLock
{
	private KMap<String, ReentrantLock> locks;
	private ReentrantLock lock;

	public MasterLock()
	{
		locks = new KMap<>();
		lock = new ReentrantLock();
	}

	public void clear()
	{
		locks.clear();
	}

	public void lock(String key)
	{
		lock.lock();
		if(!locks.containsKey(key))
		{
			locks.put(key, new ReentrantLock());
		}

		ReentrantLock l = locks.get(key);
		lock.unlock();
		l.lock();
	}

	public void unlock(String key)
	{
		lock.lock();
		if(!locks.containsKey(key))
		{
			locks.put(key, new ReentrantLock());
		}

		ReentrantLock l = locks.get(key);
		lock.unlock();
		l.unlock();
	}
}
