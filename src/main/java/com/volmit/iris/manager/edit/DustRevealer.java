package com.volmit.iris.manager.edit;

import com.volmit.iris.util.BlockPosition;
import com.volmit.iris.util.J;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;
import com.volmit.iris.scaffold.engine.EngineCompositeGenerator;
import com.volmit.iris.scaffold.parallax.ParallaxAccess;
import lombok.Data;
import org.bukkit.World;
import org.bukkit.block.Block;

@Data
public class DustRevealer {
    private final ParallaxAccess parallax;
    private final World world;
    private final BlockPosition block;
    private final String key;
    private final KList<BlockPosition> hits;

    public static void spawn(Block block)
    {
        World world = block.getWorld();

        if(world.getGenerator() instanceof EngineCompositeGenerator)
        {
            ParallaxAccess a = ((EngineCompositeGenerator)world.getGenerator()).getComposite().getEngineForHeight(block.getY()).getParallax();

            if(a.getObject(block.getX(), block.getY(), block.getZ()) != null)
            {
                J.a(() -> {
                    new DustRevealer(a, world, new BlockPosition(block.getX(), block.getY(), block.getZ()), a.getObject(block.getX(), block.getY(), block.getZ()), new KList<>());
                });
            }
        }
    }

    public DustRevealer(ParallaxAccess parallax, World world, BlockPosition block, String key, KList<BlockPosition> hits) {
        this.parallax = parallax;
        this.world = world;
        this.block = block;
        this.key = key;
        this.hits = hits;

        J.s(() -> {
            new BlockSignal(world.getBlockAt(block.getX(), block.getY(), block.getZ()), 100);
            J.a(() -> {
                is(new BlockPosition(block.getX() + 1, block.getY(), block.getZ()));
                is(new BlockPosition(block.getX() - 1, block.getY(), block.getZ()));
                is(new BlockPosition(block.getX(), block.getY() + 1, block.getZ()));
                is(new BlockPosition(block.getX(), block.getY() - 1, block.getZ()));
                is(new BlockPosition(block.getX(), block.getY(), block.getZ() + 1));
                is(new BlockPosition(block.getX(), block.getY(), block.getZ() - 1));
                is(new BlockPosition(block.getX() + 1, block.getY(), block.getZ() + 1));
                is(new BlockPosition(block.getX() + 1, block.getY(), block.getZ() - 1));
                is(new BlockPosition(block.getX() - 1, block.getY(), block.getZ() + 1));
                is(new BlockPosition(block.getX() - 1, block.getY(), block.getZ() - 1));
                is(new BlockPosition(block.getX() + 1, block.getY() + 1, block.getZ()));
                is(new BlockPosition(block.getX() + 1, block.getY() - 1, block.getZ()));
                is(new BlockPosition(block.getX() - 1, block.getY() + 1, block.getZ()));
                is(new BlockPosition(block.getX() - 1, block.getY() - 1, block.getZ()));
                is(new BlockPosition(block.getX(), block.getY() + 1, block.getZ() - 1));
                is(new BlockPosition(block.getX(), block.getY() + 1, block.getZ() + 1));
                is(new BlockPosition(block.getX(), block.getY() - 1, block.getZ() - 1));
                is(new BlockPosition(block.getX(), block.getY() - 1, block.getZ() + 1));
                is(new BlockPosition(block.getX()-1, block.getY() + 1, block.getZ() - 1));
                is(new BlockPosition(block.getX()-1, block.getY() + 1, block.getZ() + 1));
                is(new BlockPosition(block.getX()-1, block.getY() - 1, block.getZ() - 1));
                is(new BlockPosition(block.getX()-1, block.getY() - 1, block.getZ() + 1));
                is(new BlockPosition(block.getX()+1, block.getY() + 1, block.getZ() - 1));
                is(new BlockPosition(block.getX()+1, block.getY() + 1, block.getZ() + 1));
                is(new BlockPosition(block.getX()+1, block.getY() - 1, block.getZ() - 1));
                is(new BlockPosition(block.getX()+1, block.getY() - 1, block.getZ() + 1));
            });
        }, RNG.r.i(3,6));
    }

    private boolean is(BlockPosition a) {
        if(isValidTry(a) && parallax.getObject(a.getX(), a.getY(), a.getZ()) != null && parallax.getObject(a.getX(), a.getY(), a.getZ()).equals(key))
        {
            hits.add(a);
            new DustRevealer(parallax, world, a, key, hits);
        }

        return false;
    }

    private boolean isValidTry(BlockPosition b)
    {
        return !hits.contains(b);
    }
}
