package com.volmit.iris.generator.legacy.atomics;

import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.KMap;

public class MasterLock
{
	private final KMap<String, IrisLock> locks;
	private final IrisLock lock;
	private boolean enabled;

	public MasterLock()
	{
		enabled = true;
		locks = new KMap<>();
		lock = new IrisLock("MasterLock");
	}

	public void clear()
	{
		locks.clear();
	}

	public void disable()
	{
		enabled = false;
	}

	public void lock(String key)
	{
		if(!enabled)
		{
			return;
		}

		lock.lock();
		if(!locks.containsKey(key))
		{
			locks.put(key, new IrisLock("Locker"));
		}

		IrisLock l = locks.get(key);
		lock.unlock();
		l.lock();
	}

	public void unlock(String key)
	{
		if(!enabled)
		{
			return;
		}
		
		lock.lock();
		if(!locks.containsKey(key))
		{
			locks.put(key, new IrisLock("Unlocker"));
		}

		IrisLock l = locks.get(key);
		lock.unlock();
		l.unlock();
	}
}
