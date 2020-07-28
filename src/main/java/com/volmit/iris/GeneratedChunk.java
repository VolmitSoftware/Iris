package ninja.bytecode.iris;

import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import lombok.Data;
import ninja.bytecode.iris.util.ChunkPosition;

@Data
public class GeneratedChunk
{
	private ChunkPosition pos;
	private ChunkData data;
	private BiomeGrid grid;
	
	public GeneratedChunk(ChunkPosition pos)
	{
		this.pos = pos;
	}
}
