package com.volmit.iris.generator.modifier;

import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.object.NoiseStyle;
import com.volmit.iris.util.*;
import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.scaffold.engine.EngineAssignedModifier;
import com.volmit.iris.scaffold.hunk.Hunk;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class IrisRavineModifier extends EngineAssignedModifier<BlockData> {
    private static final BlockData CAVE_AIR = B.get("CAVE_AIR");
    private static final BlockData LAVA = B.get("LAVA");
    private CNG cng;
    private RNG rng;

    public IrisRavineModifier(Engine engine) {
        super(engine, "Ravine");
        rng = new RNG(getEngine().getWorld().getSeed()).nextParallelRNG(29596878);
        cng = NoiseStyle.IRIS_THICK.create(rng);
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output) {
        if(!getDimension().isRavines())
        {
            return;
        }

        PrecisionStopwatch p = PrecisionStopwatch.start();
        generateRavines(rng, Math.floorDiv(x, 16), Math.floorDiv(z, 16), output);
        getEngine().getMetrics().getRavine().put(p.getMilliseconds());
    }

    private void set(Hunk<BlockData> pos, int x, int y, int z, BlockData b)
    {
        pos.set(x, y, z, b);
    }

    private BlockData get(Hunk<BlockData> pos, int x, int y, int z)
    {
        BlockData  bb = pos.get(x, y, z);

        if(bb == null)
        {
            bb =  CAVE_AIR;
        }

        return bb;
    }

    private BlockData getSurfaceBlock(int n6, int i, RNG rmg)
    {
        return getComplex().getTrueBiomeStream().get(n6,i).getSurfaceBlock(n6, i, rmg, getData());
    }

    private float[] ravineCache = new float[1024];

    private void doRavine(long seed, int tx, int tz, ChunkPosition pos, double sx, double sy, double sz, float f, float f2, float f3, int n3, int n4, double d4, RNG bbx, Hunk<BlockData> terrain)
    {
        int n5;
        RNG random = new RNG(seed);
        double x = tx * 16 + 8;
        double z = tz * 16 + 8;
        float f4 = 0.0f;
        float f5 = 0.0f;
        if(n4 <= 0)
        {
            n5 = 8 * 16 - 16;
            n4 = n5 - random.nextInt(n5 / 4);
        }
        n5 = 0;
        if(n3 == -1)
        {
            n3 = n4 / 2;
            n5 = 1;
        }
        float f6 = 1.0f;
        // TODO: WARNING HEIGHT
        for(int i = 0; i < 256; ++i)
        {
            if(i == 0 || random.nextInt(getDimension().getRavineRibRarity()) == 0)
            {
                f6 = 1.0f + random.nextFloat() * random.nextFloat() * 1.0f;
            }
            this.ravineCache[i] = f6 * f6;
        }
        while(n3 < n4)
        {
            double d7 = 1.5 + (double) (MathHelper.sin((float) n3 * 3.1415927f / (float) n4) * f * 1.0f);
            double d8 = d7 * d4;
            d7 *= (double) random.nextFloat() * 0.25 + 0.75;
            d8 *= (double) random.nextFloat() * 0.25 + 0.75;
            float f7 = MathHelper.cos(f3);
            float f8 = MathHelper.sin(f3);
            sx = sx + (double) (MathHelper.cos(f2) * f7);
            sy += f8;
            sz += MathHelper.sin(f2) * f7;
            f3 *= 0.7f;
            f3 += f5 * 0.05f;
            f2 += f4 * 0.05f;
            f5 *= 0.8f;
            f4 *= 0.5f;
            f5 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0f;
            f4 += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0f;
            if(n5 != 0 || random.nextInt(4) != 0)
            {
                double d9 = sx - x;
                double d10 = sz - z;
                double d11 = n4 - n3;
                double d12 = f + 2.0f + 16.0f;
                if(d9 * d9 + d10 * d10 - d11 * d11 > d12 * d12)
                {
                    return;
                }
                if(sx >= x - 16.0 - d7 * 2.0 && sz >= z - 16.0 - d7 * 2.0 && sx <= x + 16.0 + d7 * 2.0 && sz <= z + 16.0 + d7 * 2.0)
                {
                    int n6;
                    int n7 = MathHelper.floor(sx - d7) - tx * 16 - 1;
                    int n8 = MathHelper.floor(sx + d7) - tx * 16 + 1;
                    int n9 = MathHelper.floor(sy - d8) - 1;
                    int n10 = MathHelper.floor(sy + d8) + 1;
                    int n11 = MathHelper.floor(sz - d7) - tz * 16 - 1;
                    int n12 = MathHelper.floor(sz + d7) - tz * 16 + 1;
                    if(n7 < 0)
                    {
                        n7 = 0;
                    }
                    if(n8 > 16)
                    {
                        n8 = 16;
                    }
                    if(n9 < 1)
                    {
                        n9 = 1;
                    }
                    if(n10 > 248)
                    {
                        n10 = 248;
                    }
                    if(n11 < 0)
                    {
                        n11 = 0;
                    }
                    if(n12 > 16)
                    {
                        n12 = 16;
                    }
                    boolean bl = false;
                    for(int i = n7; !bl && i < n8; ++i)
                    {
                        for(n6 = n11; !bl && n6 < n12; ++n6)
                        {
                            for(int j = n10 + 1; !bl && j >= n9 - 1; --j)
                            {
                                // TODO: WARNING HEIGHT
                                if(j < 0 || j >= 256)
                                {
                                    continue;
                                }

                                BlockData bb = get(terrain, i, j, n6);

                                if(B.isWater(bb))
                                {
                                    bl = true;
                                }

                                if(j == n9 - 1 || i == n7 || i == n8 - 1 || n6 == n11 || n6 == n12 - 1)
                                {
                                    continue;
                                }
                                j = n9;
                            }
                        }
                    }
                    if(!bl) {
                        BlockPosition bps = new BlockPosition(0, 0, 0);
                        for (n6 = n7; n6 < n8; ++n6) {
                            double d13 = ((double) (n6 + tx * 16) + 0.5 - sx) / d7;
                            for (int i = n11; i < n12; ++i) {
                                double d14 = ((double) (i + tz * 16) + 0.5 - sz) / d7;
                                boolean bl2 = false;
                                if (d13 * d13 + d14 * d14 >= 1.0) {
                                    continue;
                                }
                                for (int j = n10; j > n9; --j) {
                                    double d15 = ((double) (j - 1) + 0.5 - sy) / d8;
                                    if ((d13 * d13 + d14 * d14) * (double) this.ravineCache[j - 1] + d15 * d15 / 6.0 >= 1.0) {
                                        continue;
                                    }

                                    BlockData blockData = get(terrain, n6, j, i);

                                    if (isSurface(blockData)) {
                                        bl2 = true;
                                    }

                                    if (j - 1 < 10) {
                                        set(terrain, n6, j, i, LAVA);
                                        continue;
                                    }

                                    set(terrain, n6, j, i, CAVE_AIR);
                                    if (!bl2 || !isDirt(get(terrain, n6, j - 1, i))) {
                                        continue;
                                    }

                                    cSet(bps, n6 + tx * 16, 0, i + tz * 16);
                                    set(terrain, n6, j - 1, i, getSurfaceBlock(n6, i, rng));
                                }
                            }
                        }
                        if (n5 != 0)
                        {
                            break;
                        }
                    }
                }
            }
            ++n3;
        }
    }

    private BlockPosition cSet(BlockPosition bb, double var0, double var2, double var4)
    {
        bb.setX(MathHelper.floor((double) var0));
        bb.setY(MathHelper.floor((double) var2));
        bb.setZ(MathHelper.floor((double) var4));

        return bb;
    }

    private boolean isDirt(BlockData d)
    {
        //@builder
        Material m = d.getMaterial();
        return m.equals(Material.DIRT) ||
                m.equals(Material.COARSE_DIRT) ||
                m.equals(Material.SAND);
        //@done
    }

    private boolean isSurface(BlockData d)
    {
        //@builder
        Material m = d.getMaterial();
        return m.equals(Material.GRASS_BLOCK) ||
                m.equals(Material.DIRT) ||
                m.equals(Material.COARSE_DIRT) ||
                m.equals(Material.PODZOL) ||
                m.equals(Material.SAND);
        //@done
    }

    public void genRavines(int n, int n2, ChunkPosition chunkSnapshot, RNG bbb, Hunk<BlockData> terrain)
    {
        RNG b = this.rng.nextParallelRNG(21949666);
        RNG bx = this.rng.nextParallelRNG(6676121);
        long l = b.nextLong();
        long l2 = b.nextLong();
        for(int i = n - 8; i <= n + 8; ++i)
        {
            for(int j = n2 - 8; j <= n2 + 8; ++j)
            {
                long l3 = (long) i * l;
                long l4 = (long) j * l2;
                bx = this.rng.nextParallelRNG((int) (l3 ^ l4 ^ 6676121));
                doRavines(i, j, n, n2, chunkSnapshot, bx, terrain);
            }
        }
    }

    private void doRavines(int tx, int tz, int sx, int sz, ChunkPosition chunkSnapshot, RNG b, Hunk<BlockData> terrain)
    {
        if(b.nextInt(getDimension().getRavineRarity()) != 0)
        {
            return;
        }

        double x = tx * 16 + b.nextInt(16);
        double d2 = b.nextInt(b.nextInt(40) + 8) + 20;
        double z = tz * 16 + b.nextInt(16);
        int n5 = 1;
        for(int i = 0; i < n5; ++i)
        {
            float f = b.nextFloat() * 3.1415927f * 2.0f;
            float f2 = (b.nextFloat() - 0.5f) * 2.0f / 8.0f;
            float f3 = (b.nextFloat() * 2.0f + b.nextFloat()) * 2.0f;
            this.doRavine(b.nextLong(), sx, sz, chunkSnapshot, x, d2, z, f3, f, f2, 0, 0, 3.0, b, terrain);
        }
    }

    public void generateRavines(RNG nextParallelRNG, int x, int z, Hunk<BlockData> terrain)
    {
        genRavines(x, z, new ChunkPosition(x, z), nextParallelRNG.nextParallelRNG(x).nextParallelRNG(z), terrain);
    }
}
