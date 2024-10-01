package com.volmit.iris.core.nms;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;

public interface IMemoryWorld extends Listener, AutoCloseable {

    World getBukkit();

    Chunk getChunk(int x, int z);

    ChunkGenerator.ChunkData getChunkData(int x, int z);
}
