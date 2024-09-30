package com.volmit.iris.core.tools;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.Spiraler;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public class IrisWorldMerger {
    private Engine engine;
    private World world;
    private World selectedWorld;

    /**
     * @param world  > The selected world to get the caves from
     * @param engine > The engine of the iris world
     */
    public IrisWorldMerger(Engine engine, World world) {
        this.engine = engine;
        this.world = this.engine.getWorld().realWorld();
        this.selectedWorld = world;
    }

    /**
     * Merges caves from a selected chunk into the corresponding chunk in the outcome world.
     *
     * @param selectedChunk The chunk from the selected world.
     * @param targetChunk   The corresponding chunk in the outcome world.
     */
    private void mergeCavesInChunk(Chunk selectedChunk, Chunk targetChunk) {
        int baseX = selectedChunk.getX() << 4;
        int baseZ = selectedChunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            int worldX = baseX + x;
            for (int z = 0; z < 16; z++) {
                int worldZ = baseZ + z;
                int surfaceY = engine.getHeight(worldX, worldZ);
                for (int y = 0; y <= surfaceY; y++) {
                    Block selectedBlock = selectedChunk.getBlock(x, y, z);
                    if (selectedBlock.getType() == Material.AIR) {
                        Block targetBlock = targetChunk.getBlock(x, y, z);
                        targetBlock.setType(Material.AIR);
                    }
                }
            }
        }
    }

    /**
     * Irritates (merges) caves in a spiral pattern around the specified center chunk coordinates.
     *
     * @param centerX The X coordinate of the center chunk.
     * @param centerZ The Z coordinate of the center chunk.
     * @param radius  The radius (in chunks) to merge caves around.
     */
    public void irritateSpiral(int centerX, int centerZ, int radius) {
        Spiraler spiraler = new Spiraler(radius * 2, radius * 2, (x, z) -> {
            int chunkX = centerX + x;
            int chunkZ = centerZ + z;
            Chunk selectedChunk = selectedWorld.getChunkAt(chunkX, chunkZ);
            Chunk targetChunk = world.getChunkAt(chunkX, chunkZ);
            mergeCavesInChunk(selectedChunk, targetChunk);
        });

        // Execute the spiral iteration
        while (spiraler.hasNext()) {
            spiraler.next(); // The spiraler itself runs the callback defined in its constructor
        }
    }
}