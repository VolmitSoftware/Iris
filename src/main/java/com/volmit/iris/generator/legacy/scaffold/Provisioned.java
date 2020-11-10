package com.volmit.iris.generator.legacy.scaffold;

public interface Provisioned
{
	public void clearRegeneratedLists();

	public TerrainProvider getProvider();

	public void regenerate(int x, int z);
}
