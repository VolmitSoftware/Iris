package com.volmit.iris.gen.atomics;

import java.util.function.Supplier;

import com.volmit.iris.util.IrisLock;
import com.volmit.iris.util.M;

public class AtomicCache<T>
{
	private transient volatile T t;
	private transient volatile long a;
	private transient volatile int validations;
	private final IrisLock check;
	private final IrisLock time;
	private final IrisLock write;

	public AtomicCache()
	{
		check = new IrisLock("Check");
		write = new IrisLock("Write");
		time = new IrisLock("Time");
		validations = 0;
		a = -1;
		t = null;
	}

	public void reset()
	{
		check.lock();
		write.lock();
		time.lock();
		a = -1;
		t = null;
		time.unlock();
		write.unlock();
		check.unlock();
	}

	public T aquire(Supplier<T> t)
	{
		if(this.t != null && validations > 1000)
		{
			return this.t;
		}

		if(this.t != null && M.ms() - a > 1000)
		{
			if(this.t != null)
			{
				validations++;
			}

			return this.t;
		}

		check.lock();

		if(this.t == null)
		{
			write.lock();
			this.t = t.get();

			time.lock();

			if(a == -1)
			{
				a = M.ms();
			}

			time.unlock();
			write.unlock();
		}

		check.unlock();
		return this.t;
	}
}
