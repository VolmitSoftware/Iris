package com.volmit.iris.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentSkipListMap;

public class Violator
{
	protected static ConcurrentSkipListMap<String, Object> nodes = new ConcurrentSkipListMap<String, Object>();

	private static String id(Object o, Object h)
	{
		if(o instanceof Field)
		{
			return id(((Field) o).getDeclaringClass(), null) + "." + ((Field) o).getName();
		}

		if(o instanceof String)
		{
			return (String) o;
		}

		if(o instanceof Class<?>)
		{
			return ((Class<?>) o).getCanonicalName();
		}

		if(o instanceof Constructor<?>)
		{
			Constructor<?> co = (Constructor<?>) o;

			String mx = "";

			for(Class<?> i : co.getParameterTypes())
			{
				mx += "," + i.getCanonicalName();
			}

			mx = mx.length() >= 1 ? mx.substring(1) : mx;

			return id(co.getDeclaringClass(), null) + "(" + mx + ")";
		}

		if(o instanceof Method)
		{
			String mx = "";

			for(Class<?> i : ((Method) o).getParameterTypes())
			{
				mx += "," + i.getCanonicalName();
			}

			mx = mx.length() >= 1 ? mx.substring(1) : mx;

			return id(((Method) o).getDeclaringClass(), null) + "." + ((Method) o).getName() + "(" + mx + ")";
		}

		if(o instanceof Annotation)
		{
			Annotation a = (Annotation) o;
			return "@" + a.annotationType().getCanonicalName() + "[" + id(h, null) + "]";
		}

		return o.hashCode() + o.toString();
	}

	private static void p(String n, Object o)
	{
		nodes.put(n, o);
	}

	private static boolean h(String n)
	{
		return nodes.containsKey(n);
	}

	private static Object g(String n)
	{
		return nodes.get(n);
	}

	public static Constructor<?> getConstructor(Class<?> c, Class<?>... params) throws NoSuchMethodException, SecurityException
	{
		String mx = "";

		for(Class<?> i : params)
		{
			mx += "," + i.getCanonicalName();
		}

		mx = mx.length() >= 1 ? mx.substring(1) : mx;

		if(!h(id(c, null) + "(" + mx + ")"))
		{
			Constructor<?> co = c.getConstructor(params);
			co.setAccessible(true);
			p(id(co, null), co);
		}

		return (Constructor<?>) g(id(c, null) + "(" + mx + ")");
	}

	@SuppressWarnings("rawtypes")
	public static Field getField(Class<?> c, String name) throws Throwable
	{
		if(!h(id(c, null) + "." + name))
		{
			try
			{
				Field f = c.getField(name);
				f.setAccessible(true);
				p(id(c, null) + "." + name, f);
			}
			catch(NoSuchFieldException e)
			{
				Class s = c.getSuperclass();
				if(null == s)
				{
					throw e;
				}
				Field f = s.getField(name);
				f.setAccessible(true);
				p(id(c, null) + "." + name, f);
			}
		}

		return (Field) g(id(c, null) + "." + name);
	}

	@SuppressWarnings("rawtypes")
	public static Field getDeclaredField(Class<?> c, String name) throws Throwable
	{
		if(!h(id(c, null) + "." + name))
		{
			try
			{
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				p(id(c, null) + "." + name, f);
			}
			catch(NoSuchFieldException e)
			{
				Class s = c.getSuperclass();
				if(null == s)
				{
					throw e;
				}
				Field f = s.getDeclaredField(name);
				f.setAccessible(true);
				p(id(c, null) + "." + name, f);
			}
		}

		return (Field) g(id(c, null) + "." + name);
	}

	public static Method getMethod(Class<?> c, String name, Class<?>... pars) throws Throwable
	{
		String iv = "";
		String mx = "";

		for(Class<?> i : pars)
		{
			mx += "," + i.getCanonicalName();
		}

		mx = mx.length() >= 1 ? mx.substring(1) : mx;
		iv = id(c, null) + "." + name + "(" + mx + ")";

		if(!h(iv))
		{
			Method f = c.getMethod(name, pars);
			f.setAccessible(true);
			p(iv, f);
		}

		return (Method) g(iv);
	}

	@SuppressWarnings("unchecked")
	public static <T> T construct(Class<?> c, Object... parameters)
	{
		KList<Class<?>> cv = new KList<Class<?>>();

		for(Object i : parameters)
		{
			cv.add(i.getClass());
		}

		try
		{
			Constructor<?> co = getConstructor(c, cv.toArray(new Class<?>[cv.size()]));
			return (T) co.newInstance(parameters);
		}

		catch(Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public static Method getDeclaredMethod(Class<?> c, String name, Class<?>... pars) throws Throwable
	{
		String iv = "";
		String mx = "";

		for(Class<?> i : pars)
		{
			mx += "," + i.getCanonicalName();
		}

		mx = mx.length() >= 1 ? mx.substring(1) : mx;
		iv = id(c, null) + "." + name + "(" + mx + ")";

		if(!h(iv))
		{
			Method f = c.getDeclaredMethod(name, pars);
			f.setAccessible(true);
			p(iv, f);
		}

		return (Method) g(iv);
	}

	@SuppressWarnings("unchecked")
	public static <T extends Annotation> T getAnnotation(Class<?> c, Class<? extends T> a) throws Throwable
	{
		if(!h("@" + a.getCanonicalName() + "[" + c.getCanonicalName() + "]"))
		{
			T f = c.getAnnotation(a);
			p(id(f, c), f);
		}

		return (T) g("@" + a.getCanonicalName() + "[" + c.getCanonicalName() + "]");
	}

	@SuppressWarnings("unchecked")
	public static <T extends Annotation> T getDeclaredAnnotation(Class<?> c, Class<? extends T> a) throws Throwable
	{
		if(!h("@" + a.getCanonicalName() + "[" + c.getCanonicalName() + "]"))
		{
			T f = c.getDeclaredAnnotation(a);
			p(id(f, c), f);
		}

		return (T) g("@" + a.getCanonicalName() + "[" + c.getCanonicalName() + "]");
	}

	@SuppressWarnings("unchecked")
	public static <T extends Annotation> T getAnnotation(Field c, Class<? extends T> a) throws Throwable
	{
		if(!h("@" + a.getCanonicalName() + "[" + id(c, null) + "]"))
		{
			T f = c.getAnnotation(a);
			p(id(f, c), f);
		}

		return (T) g("@" + a.getCanonicalName() + "[" + id(c, null) + "]");
	}

	@SuppressWarnings("unchecked")
	public static <T extends Annotation> T getDeclaredAnnotation(Field c, Class<? extends T> a) throws Throwable
	{
		if(!h("@" + a.getCanonicalName() + "[" + id(c, null) + "]"))
		{
			T f = c.getDeclaredAnnotation(a);
			p(id(f, c), f);
		}

		return (T) g("@" + a.getCanonicalName() + "[" + id(c, null) + "]");
	}

	@SuppressWarnings("unchecked")
	public static <T extends Annotation> T getAnnotation(Method c, Class<? extends T> a) throws Throwable
	{
		if(!h("@" + a.getCanonicalName() + "[" + id(c, null) + "]"))
		{
			T f = c.getAnnotation(a);
			p(id(f, c), f);
		}

		return (T) g("@" + a.getCanonicalName() + "[" + id(c, null) + "]");
	}

	@SuppressWarnings("unchecked")
	public static <T extends Annotation> T getDeclaredAnnotation(Method c, Class<? extends T> a) throws Throwable
	{
		if(!h("@" + a.getCanonicalName() + "[" + id(c, null) + "]"))
		{
			T f = c.getDeclaredAnnotation(a);
			p(id(f, c), f);

			System.out.println("Set as " + id(f, c) + " as " + ("@" + a.getCanonicalName() + "[" + id(c, null) + "]"));
		}

		return (T) g("@" + a.getCanonicalName() + "[" + id(c, null) + "]");
	}
}
