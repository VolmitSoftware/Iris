package com.volmit.iris.util;

public class Wrapper<T>
{
	private T t;

	public Wrapper(T t)
	{
		set(t);
	}

	public void set(T t)
	{
		this.t = t;
	}

	public T get()
	{
		return t;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((t == null) ? 0 : t.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
		{
			return true;
		}
		if(obj == null)
		{
			return false;
		}
		if(!(obj instanceof Wrapper))
		{
			return false;
		}

		Wrapper<?> other = (Wrapper<?>) obj;
		if(t == null)
		{
			if(other.t != null)
			{
				return false;
			}
		}
		else if(!t.equals(other.t))
		{
			return false;
		}
		return true;
	}

	@Override
	public String toString()
	{
		if(t != null)
		{
			return get().toString();
		}

		return super.toString() + " (null)";
	}
}
