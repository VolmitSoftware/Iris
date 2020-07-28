package com.volmit.iris.util;

public interface NastyFunction<T, R>
{
	public R run(T t) throws Throwable;
}
