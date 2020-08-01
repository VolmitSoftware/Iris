package com.volmit.iris.util;

import org.bukkit.event.Listener;

public interface IController extends Listener
{
	public String getName();

	public void start();

	public void stop();

	public void tick();

	public int getTickInterval();

	public void l(Object l);

	public void w(Object l);

	public void f(Object l);

	public void v(Object l);
}
