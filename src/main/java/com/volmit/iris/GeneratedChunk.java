package com.volmit.iris;

import org.bukkit.generator.ChunkGenerator.BiomeGrid;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import com.volmit.iris.util.ChunkPosition;

import lombok.Data;

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
