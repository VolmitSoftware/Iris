package com.volmit.iris.util;

public interface NastyFuture<R>
{
	public R run() throws Throwable;
}
