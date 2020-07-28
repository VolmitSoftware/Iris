package com.volmit.iris.util;

public interface Observable<T>
{
	public T get();
	
	public Observable<T> set(T t);
	
	public boolean has();
	
	public Observable<T> clearObservers();
	
	public Observable<T> observe(Observer<T> t);
}
