package com.volmit.iris.util;

import com.volmit.iris.Iris;

public abstract class Controller implements IController
{
	private int tickRate;
	private String name;

	public Controller()
	{
		name = getClass().getSimpleName().replaceAll("Controller", "") + " Controller";
		tickRate = -1;
	}

	protected void setTickRate(int rate)
	{
		this.tickRate = rate;
	}

	protected void disableTicking()
	{
		setTickRate(-1);
	}

	@Override
	public void l(Object l)
	{
		Iris.info("[" + getName() + "]: " + l);
	}

	@Override
	public void w(Object l)
	{
		Iris.warn("[" + getName() + "]: " + l);
	}

	@Override
	public void f(Object l)
	{
		Iris.error("[" + getName() + "]: " + l);
	}

	@Override
	public void v(Object l)
	{
		Iris.verbose("[" + getName() + "]: " + l);
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public abstract void start();

	@Override
	public abstract void stop();

	@Override
	public abstract void tick();

	@Override
	public int getTickInterval()
	{
		return tickRate;
	}
}
