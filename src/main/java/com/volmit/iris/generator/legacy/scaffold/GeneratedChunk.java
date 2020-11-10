package com.volmit.iris.generator.legacy.scaffold;

import com.volmit.iris.generator.legacy.atomics.AtomicSliverMap;
import com.volmit.iris.util.BiomeMap;
import com.volmit.iris.util.HeightMap;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class GeneratedChunk
{
	private int x;
	private int z;
	private TerrainChunk terrain;
	private HeightMap height;
	private AtomicSliverMap sliverMap;
	private BiomeMap biomeMap;
}
