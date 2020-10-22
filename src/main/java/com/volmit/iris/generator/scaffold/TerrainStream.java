package com.volmit.iris.generator.scaffold;

import com.volmit.iris.generator.atomics.TerrainHunk;

public interface TerrainStream
{
	public TerrainHunk generate(int x1, int z1, int x2, int z2);
	
	public TerrainHunk generate(int x, int z);
}
