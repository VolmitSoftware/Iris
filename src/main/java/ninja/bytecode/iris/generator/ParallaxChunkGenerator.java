package ninja.bytecode.iris.generator;

import lombok.Data;
import lombok.EqualsAndHashCode;
import ninja.bytecode.iris.util.HeightMap;
import ninja.bytecode.iris.util.RNG;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class ParallaxChunkGenerator extends TerrainChunkGenerator
{
	public ParallaxChunkGenerator(String dimensionName, int threads)
	{
		super(dimensionName, threads);
	}
	
	@Override
	protected void onPostGenerate(RNG random, int x, int z, ChunkData data, BiomeGrid grid, HeightMap height)
	{
		
	}
}
