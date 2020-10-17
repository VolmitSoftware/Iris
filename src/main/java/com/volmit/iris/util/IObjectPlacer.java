package com.volmit.iris.util;

public interface IObjectPlacer
{
	public int getHighest(int x, int z);

	public int getHighest(int x, int z, boolean ignoreFluid);

	public void set(int x, int y, int z, FastBlockData d);

	public FastBlockData get(int x, int y, int z);

	public boolean isPreventingDecay();

	public boolean isSolid(int x, int y, int z);

	public boolean isUnderwater(int x, int z);

	public int getFluidHeight();

	public boolean isDebugSmartBore();
}
