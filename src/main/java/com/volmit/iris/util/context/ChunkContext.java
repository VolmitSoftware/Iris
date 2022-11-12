package com.volmit.iris.util.context;

import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import lombok.Data;
import org.bukkit.block.data.BlockData;

@Data
public class ChunkContext {
    private final int x;
    private final int z;
    private ChunkedDataCache<Double> height;
    private ChunkedDataCache<IrisBiome> biome;
    private ChunkedDataCache<IrisBiome> cave;
    private ChunkedDataCache<BlockData> rock;
    private ChunkedDataCache<BlockData> fluid;
    private ChunkedDataCache<IrisRegion> region;

    @BlockCoordinates
    public ChunkContext(int x, int z, IrisComplex c) {
        this(x, z, c, true);
    }

    @BlockCoordinates
    public ChunkContext(int x, int z, IrisComplex c, boolean cache) {
        this.x = x;
        this.z = z;

        if (cache) {
            BurstExecutor b = MultiBurst.burst.burst();
            height = new ChunkedDataCache<>(b, c.getHeightStream(), x, z);
            biome = new ChunkedDataCache<>(b, c.getTrueBiomeStream(), x, z);
            cave = new ChunkedDataCache<>(b, c.getCaveBiomeStream(), x, z);
            rock = new ChunkedDataCache<>(b, c.getRockStream(), x, z);
            fluid = new ChunkedDataCache<>(b, c.getFluidStream(), x, z);
            region = new ChunkedDataCache<>(b, c.getRegionStream(), x, z);
            b.complete();
        } else {
            height = new ChunkedDataCache<>(null, c.getHeightStream(), x, z, false);
            biome = new ChunkedDataCache<>(null, c.getTrueBiomeStream(), x, z, false);
            cave = new ChunkedDataCache<>(null, c.getCaveBiomeStream(), x, z, false);
            rock = new ChunkedDataCache<>(null, c.getRockStream(), x, z, false);
            fluid = new ChunkedDataCache<>(null, c.getFluidStream(), x, z, false);
            region = new ChunkedDataCache<>(null, c.getRegionStream(), x, z, false);
        }
    }
}
