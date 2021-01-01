package com.volmit.iris.manager.edit;

import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.scaffold.engine.IrisAccess;
import com.volmit.iris.scaffold.parallax.ParallaxAccess;
import com.volmit.iris.util.*;
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

    public static void spawn(Block block, MortarSender sender)
    {
        World world = block.getWorld();
        IrisAccess access = IrisWorlds.access(world);

        if(access != null)
        {
            ParallaxAccess a = access.getEngineAccess(block.getY()).getParallaxAccess();

            if(a.getObject(block.getX(), block.getY(), block.getZ()) != null)
            {
                sender.sendMessage("Found object " + a.getObject(block.getX(), block.getY(), block.getZ()));
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
                try {
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
                }

                catch(Throwable e)
                {
                    e.printStackTrace();
                }
            });
        }, RNG.r.i(3,6));
    }

    private boolean is(BlockPosition a) {
        if(isValidTry(a) && parallax.getObject(a.getX(), a.getY(), a.getZ()) != null && parallax.getObject(a.getX(), a.getY(), a.getZ()).equals(key))
        {
            hits.add(a);
            new DustRevealer(parallax, world, a, key, hits);
            return true;
        }

        return false;
    }

    private boolean isValidTry(BlockPosition b)
    {
        return !hits.contains(b);
    }
}
