package com.volmit.iris.manager.edit;

import com.volmit.iris.util.J;
import com.volmit.iris.scaffold.parallel.MultiBurst;
import com.volmit.iris.util.SR;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class BlockSignal {
    public static void of(Block block, int ticks)
    {
        new BlockSignal(block, ticks);
    }

    public static void of(Block block)
    {
        of(block, 100);
    }

    public static Runnable forever(Block block)
    {
        Location tg = block.getLocation().clone().add(0.5, 0, 0.5).clone();
        FallingBlock e = block.getWorld().spawnFallingBlock(tg.clone(), block.getBlockData());
        e.setGravity(false);
        e.setInvulnerable(true);
        e.setGlowing(true);
        e.teleport(tg.clone());
        e.setDropItem(false);
        e.setHurtEntities(false);
        e.setSilent(true);
        e.setTicksLived(1);
        e.setVelocity(new Vector(0, 0, 0));

        new SR(20) {
            @Override
            public void run() {
                if(e.isDead())
                {
                    cancel();
                    return;
                }

                e.setTicksLived(1);
                e.teleport(tg.clone());
                e.setVelocity(new Vector(0, 0, 0));
            }
        };

        return () -> {
            e.remove();
            BlockData type = block.getBlockData();

            MultiBurst.burst.lazy(() -> {
                for(Player i : block.getWorld().getPlayers())
                {
                    i.sendBlockChange(block.getLocation(), block.getBlockData());
                }
            });
        };
    }

    public BlockSignal(Block block, int ticks)
    {
        Location tg = block.getLocation().clone().add(0.5, 0, 0.5).clone();
        FallingBlock e = block.getWorld().spawnFallingBlock(tg.clone(), block.getBlockData());
        e.setGravity(false);
        e.setInvulnerable(true);
        e.setGlowing(true);
        e.teleport(tg.clone());
        e.setDropItem(false);
        e.setHurtEntities(false);
        e.setSilent(true);
        e.setTicksLived(1);
        e.setVelocity(new Vector(0, 0, 0));
        J.s(() -> {
            e.remove();
            BlockData type = block.getBlockData();

            MultiBurst.burst.lazy(() -> {
                for(Player i : block.getWorld().getPlayers())
                {
                    i.sendBlockChange(block.getLocation(), block.getBlockData());
                }
            });
        }, ticks);
    }
}
