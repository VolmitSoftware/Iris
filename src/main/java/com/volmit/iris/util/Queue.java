package com.volmit.iris.util;

public interface Queue<T>
{
	public Queue<T> queue(T t);

	public Queue<T> queue(KList<T> t);

	public boolean hasNext(int amt);

	public boolean hasNext();

	public T next();

	public KList<T> next(int amt);

	public Queue<T> clear();

	public int size();

	public static <T> Queue<T> create(KList<T> t)
	{
		return new ShurikenQueue<T>().queue(t);
	}

	@SuppressWarnings("unchecked")
	public static <T> Queue<T> create(T... t)
	{
		return new ShurikenQueue<T>().queue(new KList<T>().add(t));
	}

	public boolean contains(T p);
}
