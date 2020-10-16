package com.volmit.iris.util;

import java.lang.annotation.Annotation;

public class V
{
	private Object o;
	private boolean local;
	private boolean suppress = false;

	public V(Class<?> c, Object... parameters)
	{
		this.o = Violator.construct(c, parameters);
		this.local = true;
	}

	public V(Object o)
	{
		this.o = o;
		this.local = true;
	}

	public V(Object o, boolean local, boolean suppress)
	{
		this(o);
		this.local = local;
		this.suppress = suppress;
	}

	public V(Object o, boolean local)
	{
		this(o);
		this.local = local;
	}

	public <T extends Annotation> T get(Class<? extends T> t)
	{
		try
		{
			return (T) (local ? Violator.getDeclaredAnnotation(o.getClass(), t) : Violator.getAnnotation(o.getClass(), t));
		}

		catch(Throwable e)
		{
			if(!suppress)
			{
				e.printStackTrace();
			}
		}

		return null;
	}

	public <T extends Annotation> T get(Class<? extends T> t, String mn, Class<?>... pars)
	{
		try
		{
			return (T) (local ? Violator.getDeclaredAnnotation(Violator.getDeclaredMethod(o.getClass(), mn, pars), t) : Violator.getAnnotation(Violator.getMethod(o.getClass(), mn, pars), t));
		}

		catch(Throwable e)
		{
			if(!suppress)
			{
				e.printStackTrace();
			}
		}

		return null;
	}

	public <T extends Annotation> T get(Class<? extends T> t, String mn)
	{
		try
		{
			return (T) (local ? Violator.getDeclaredAnnotation(Violator.getDeclaredField(o.getClass(), mn), t) : Violator.getAnnotation(Violator.getField(o.getClass(), mn), t));
		}

		catch(Throwable e)
		{
			if(!suppress)
			{
				e.printStackTrace();
			}
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String field)
	{
		try
		{
			return (T) (local ? Violator.getDeclaredField(o.getClass(), field) : Violator.getField(o.getClass(), field)).get(o);
		}

		catch(Throwable e)
		{
			if(!suppress)
			{
				e.printStackTrace();
			}
		}

		return null;
	}

	public Object invoke(String method, Object... parameters)
	{
		KList<Class<?>> par = new KList<Class<?>>();

		for(Object i : parameters)
		{
			par.add(i.getClass());
		}

		try
		{
			return (local ? Violator.getDeclaredMethod(o.getClass(), method, par.toArray(new Class<?>[par.size()])) : Violator.getMethod(o.getClass(), method, par.toArray(new Class<?>[par.size()]))).invoke(o, parameters);
		}

		catch(Throwable e)
		{
			if(!suppress)
			{
				e.printStackTrace();
			}
		}

		return null;
	}

	public void set(String field, Object value)
	{
		try
		{
			// https://github.com/VolmitSoftware/Mortar/issues/5
			(local ? Violator.getDeclaredField(o.getClass(), field) : Violator.getField(o.getClass(), field)).set(o, value);
		}

		catch(Throwable e)
		{
			if(!suppress)
			{
				e.printStackTrace();
			}
		}
	}
}
