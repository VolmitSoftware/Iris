package com.volmit.iris.scaffold.engine;

import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.B;
import com.volmit.iris.util.TerrainChunk;
import lombok.Data;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

import java.util.concurrent.atomic.AtomicBoolean;

@Data
public class PregeneratedData {
    private final Hunk<BlockData> blocks;
    private final Hunk<BlockData> post;
    private final Hunk<Biome> biomes;
    private final AtomicBoolean postMod;

    public PregeneratedData(int height)
    {
        postMod = new AtomicBoolean(false);
        blocks = Hunk.newAtomicHunk(16, height, 16);
        biomes = Hunk.newAtomicHunk(16, height, 16);
        Hunk<BlockData> p = Hunk.newMappedHunkSynced(16, height, 16);
        post = p.trackWrite(postMod);
    }

    public Runnable inject(TerrainChunk tc) {
        blocks.iterateSync((x, y, z, b) -> {
            if(b != null)
            {
                tc.setBlock(x, y, z, b);
            }

            Biome bf = biomes.get(x,y,z);
            if(bf != null)
            {
                tc.setBiome(x,y,z,bf);
            }
        });

        if(postMod.get())
        {
            return () -> Hunk.view((ChunkGenerator.ChunkData) tc).insertSoftly(0,0,0, post, (b) -> b == null || B.isAirOrFluid(b));
        }

        return () -> {};
    }
}
