package com.volmit.iris.generator.scaffold;

import com.volmit.iris.generator.atomics.HeightHunk;
import com.volmit.iris.generator.atomics.TerrainHunk;

public interface TerrainStream
{
	public long getSeed();

	public int getHeight();

	public int getNoise(int x, int z);

	public HeightHunk genNoise(int x1, int z1, int x2, int z2);

	public TerrainHunk genTerrain(int x1, int z1, int x2, int z2, TerrainHunk noise);

	public TerrainHunk genCarving(int x1, int z1, int x2, int z2, HeightHunk noise);

	public TerrainHunk genDecorations(int x1, int z1, int x2, int z2, TerrainHunk hunk);

	public TerrainHunk genParallax(int x1, int z1, int x2, int z2, TerrainHunk hunk);
}
