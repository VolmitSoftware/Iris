package com.volmit.iris.gen.scaffold;

public interface Provisioned
{
	public void clearRegeneratedLists();

	public TerrainProvider getProvider();

	public void regenerate(int x, int z);
}
