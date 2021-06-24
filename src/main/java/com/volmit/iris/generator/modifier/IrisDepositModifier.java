package com.volmit.iris.generator.modifier;

import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDepositGenerator;
import com.volmit.iris.object.IrisObject;
import com.volmit.iris.object.IrisRegion;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedModifier;
import com.volmit.iris.scaffold.hunk.Hunk;
import com.volmit.iris.util.HeightMap;
import com.volmit.iris.util.PrecisionStopwatch;
import com.volmit.iris.util.RNG;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

public class IrisDepositModifier extends EngineAssignedModifier<BlockData> {
    private final RNG rng;
    public IrisDepositModifier(Engine engine) {
        super(engine, "Deposit");
        rng = new RNG(getEngine().getWorld().getSeed()+12938).nextParallelRNG(28348777);
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        generateDeposits(rng, output, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        getEngine().getMetrics().getDeposit().put(p.getMilliseconds());
    }

    public void generateDeposits(RNG rx, Hunk<BlockData> terrain, int x, int z)
    {
        RNG ro = rx.nextParallelRNG(x * x).nextParallelRNG(z * z);
        IrisRegion region = getComplex().getRegionStream().get((x * 16) + 7, (z * 16) + 7);
        IrisBiome biome = getComplex().getTrueBiomeStream().get((x * 16) + 7, (z * 16) + 7);

        for(IrisDepositGenerator k : getDimension().getDeposits())
        {
            generate(k, terrain, ro, x, z, false);
        }

        for(IrisDepositGenerator k : region.getDeposits())
        {
            for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
            {
                generate(k, terrain, ro, x, z, false);
            }
        }

        for(IrisDepositGenerator k : biome.getDeposits())
        {
            for(int l = 0; l < ro.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
            {
                generate(k, terrain, ro, x, z, false);
            }
        }
    }

    public void generate(IrisDepositGenerator k, Hunk<BlockData> data, RNG rng, int cx, int cz, boolean safe)
    {
        generate(k, data, rng, cx, cz, safe, null);
    }

    public void generate(IrisDepositGenerator k, Hunk<BlockData> data, RNG rng, int cx, int cz, boolean safe, HeightMap he)
    {
        for(int l = 0; l < rng.i(k.getMinPerChunk(), k.getMaxPerChunk()); l++)
        {
            IrisObject clump = k.getClump(rng, getData());

            int af = (int) Math.ceil(clump.getW() / 2D);
            int bf = (int) Math.floor(16D - (clump.getW() / 2D));

            if(af > bf || af < 0 || bf > 15 || af > 15 || bf < 0)
            {
                af = 6;
                bf = 9;
            }

            int x = rng.i(af, bf);
            int z = rng.i(af, bf);
            int height = (he != null ? he.getHeight((cx << 4) + x, (cz << 4) + z) : (int) (Math.round(
                    getComplex().getHeightStream().get((cx << 4) + x, (cz << 4) + z)
            ))) - 7;

            if(height <= 0)
            {
                return;
            }

            int i = Math.max(0, k.getMinHeight());
            // TODO: WARNING HEIGHT
            int a = Math.min(height, Math.min(256, k.getMaxHeight()));

            if(i >= a)
            {
                return;
            }

            int h = rng.i(i, a);

            if(h > k.getMaxHeight() || h < k.getMinHeight() || h > height - 2)
            {
                return;
            }

            for(BlockVector j : clump.getBlocks().keySet())
            {
                int nx = j.getBlockX() + x;
                int ny = j.getBlockY() + h;
                int nz = j.getBlockZ() + z;

                if(ny > height || nx > 15 || nx < 0 || ny > 255 || ny < 0 || nz < 0 || nz > 15)
                {
                    continue;
                }

                boolean allow = false;

                BlockData b = data.get(nx, ny, nz);
                if(b != null) {
                    for (BlockData f : getDimension().getRockPalette().getBlockData(getData())) {
                        if (f.getMaterial().equals(b.getMaterial())) {
                            allow = true;
                            break;
                        }
                    }
                }

                if(allow)
                {
                    data.set(nx, ny, nz, clump.getBlocks().get(j));
                }
            }
        }
    }
}
