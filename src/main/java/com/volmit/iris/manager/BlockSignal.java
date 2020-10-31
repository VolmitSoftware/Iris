package com.volmit.iris.manager;

import com.volmit.iris.util.J;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;

public class BlockSignal {
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
            block.setType(Material.AIR, false);
            block.setBlockData(type, false);
        }, ticks);
    }
}
