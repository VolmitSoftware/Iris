package com.volmit.plague.api;

import com.volmit.iris.util.collection.KList;
import com.volmit.plague.api.annotations.Plagued;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PlagueRegistry
{
	public static boolean isPlagued(Field field)
	{
		return field.isAnnotationPresent(Plagued.class);
	}

	public static boolean isPlagued(Method method)
	{
		return method.isAnnotationPresent(Plagued.class);
	}

	public static boolean isPlagued(Field field, Class<?> superType)
	{
		return isPlagued(field) && superType.isAssignableFrom(field.getType());
	}

	public static KList<Method> getPlaguedMethods(Class<?> c)
	{
		KList<Method> p = new KList<>();

		for(Method i : c.getDeclaredMethods())
		{
			if(isPlagued(i))
			{
				p.add(i);
			}
		}

		return p;
	}

	public static KList<Field> getPlaguedFields(Class<?> c)
	{
		KList<Field> p = new KList<>();

		for(Field i : c.getDeclaredFields())
		{
			if(isPlagued(i))
			{
				p.add(i);
			}
		}

		return p;
	}

	public static KList<Field> getPlaguedFields(Class<?> c, Class<?> superType)
	{
		KList<Field> p = new KList<>();

		for(Field i : c.getDeclaredFields())
		{
			if(isPlagued(i, superType))
			{
				p.add(i);
			}
		}

		return p;
	}

	public static <T> T init(Class<T> c, Object... f)
	{
		if(f.length == 0)
		{
			try
			{
				return c.getConstructor().newInstance();
			}

			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		Class<?>[] k = new Class<?>[f.length];
		for(int i = 0; i < f.length; i++)
		{
			k[i] = f[i].getClass();
		}

		try
		{
			return c.getConstructor(k).newInstance(f);
		}

		catch(Throwable e)
		{
			e.printStackTrace();
		}

		return null;
	}
}
