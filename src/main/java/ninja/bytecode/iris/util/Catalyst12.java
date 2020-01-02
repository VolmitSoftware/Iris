package ninja.bytecode.iris.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;

import net.minecraft.server.v1_12_R1.IBlockData;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.shuriken.execution.J;

public class Catalyst12
{
	public static void waitForChunk(World w, int x, int z)
	{
		if(!w.isChunkLoaded(x, z))
		{
			Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> w.loadChunk(x, z, true));
		}

		int i = 0;
		while(!w.isChunkLoaded(x, z) && i < 20)
		{
			Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Iris.instance, () -> w.loadChunk(x, z, true));
			J.sleep(50);
			i++;
		}
	}

	@SuppressWarnings("deprecation")
	public static void setBlock(Location l, MB m)
	{
		int x = l.getBlockX();
		int y = l.getBlockY();
		int z = l.getBlockZ();
		net.minecraft.server.v1_12_R1.World w = ((CraftWorld) l.getWorld()).getHandle();
		net.minecraft.server.v1_12_R1.Chunk chunk = w.getChunkAt(x >> 4, z >> 4);
		int combined = m.material.getId() + (m.data << 12);
		IBlockData ibd = net.minecraft.server.v1_12_R1.Block.getByCombinedId(combined);

		if(chunk.getSections()[y >> 4] == null)
		{
			chunk.getSections()[y >> 4] = new net.minecraft.server.v1_12_R1.ChunkSection(y >> 4 << 4, chunk.world.worldProvider.m());
		}

		net.minecraft.server.v1_12_R1.ChunkSection sec = chunk.getSections()[y >> 4];

		synchronized(sec)
		{
			sec.setType(x & 15, y & 15, z & 15, ibd);
		}
	}
}